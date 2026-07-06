package dev.xj16.pocketscan.data

import kotlinx.coroutines.flow.Flow

/**
 * Thin repository over [ReceiptDao]. Exists so ViewModels depend on a single
 * domain-shaped surface rather than Room details, which also makes them
 * trivial to unit-test with a fake DAO.
 */
class ReceiptRepository(private val dao: ReceiptDao) {

    val receipts: Flow<List<ReceiptEntity>> = dao.observeAll()

    val count: Flow<Int> = dao.observeCount()

    fun totalMinor(currency: String): Flow<Long> = dao.observeTotalMinor(currency)

    suspend fun get(id: Long): ReceiptEntity? = dao.findById(id)

    suspend fun add(receipt: ReceiptEntity): Long = dao.insert(receipt)

    suspend fun update(receipt: ReceiptEntity) = dao.update(receipt)

    suspend fun remove(receipt: ReceiptEntity) = dao.delete(receipt)
}
