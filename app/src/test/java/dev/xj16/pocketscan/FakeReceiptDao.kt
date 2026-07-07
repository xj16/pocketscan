package dev.xj16.pocketscan

import dev.xj16.pocketscan.data.CategoryTotal
import dev.xj16.pocketscan.data.CurrencyTotal
import dev.xj16.pocketscan.data.ReceiptDao
import dev.xj16.pocketscan.data.ReceiptEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [ReceiptDao] for JVM unit tests. Backs a reactive list so the
 * ViewModel's Flow pipeline (search, currency grouping, category totals) can be
 * exercised without Room/Android — mirroring the DAO's documented semantics.
 */
class FakeReceiptDao(initial: List<ReceiptEntity> = emptyList()) : ReceiptDao {

    private val rows = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    fun setRows(list: List<ReceiptEntity>) { rows.value = list }

    override fun observeAll(): Flow<List<ReceiptEntity>> =
        rows.map { it.sortedByDescending { r -> r.createdAt } }

    override fun search(query: String, category: String?): Flow<List<ReceiptEntity>> =
        rows.map { list ->
            list.filter { r ->
                val q = query.trim()
                val matchesQuery = q.isEmpty() ||
                    r.merchant.contains(q, ignoreCase = true) ||
                    r.rawText.contains(q, ignoreCase = true)
                val matchesCategory = category == null || r.category == category
                matchesQuery && matchesCategory
            }.sortedByDescending { it.createdAt }
        }

    override suspend fun findById(id: Long): ReceiptEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeById(id: Long): Flow<ReceiptEntity?> =
        rows.map { list -> list.firstOrNull { it.id == id } }

    override fun observeTotalMinor(currency: String): Flow<Long> =
        rows.map { list -> list.filter { it.currency == currency }.sumOf { it.totalMinor } }

    override fun observeCurrencyTotals(): Flow<List<CurrencyTotal>> =
        rows.map { list ->
            list.groupBy { it.currency }
                .map { (cur, group) ->
                    CurrencyTotal(cur, group.sumOf { it.totalMinor }, group.size)
                }
                .sortedByDescending { it.totalMinor }
        }

    override fun observeCategoryTotals(): Flow<List<CategoryTotal>> =
        rows.map { list ->
            list.groupBy { it.category to it.currency }
                .map { (key, group) ->
                    CategoryTotal(key.first, key.second, group.sumOf { it.totalMinor }, group.size)
                }
                .sortedByDescending { it.totalMinor }
        }

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override suspend fun insert(receipt: ReceiptEntity): Long {
        val id = nextId++
        rows.value = rows.value + receipt.copy(id = id)
        return id
    }

    override suspend fun update(receipt: ReceiptEntity) {
        rows.value = rows.value.map { if (it.id == receipt.id) receipt else it }
    }

    override suspend fun delete(receipt: ReceiptEntity) {
        rows.value = rows.value.filterNot { it.id == receipt.id }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}
