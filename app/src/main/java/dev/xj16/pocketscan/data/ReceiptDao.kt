package dev.xj16.pocketscan.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A per-currency running total. Receipts are grouped by ISO-4217 code so the
 * home screen can honestly show `$142.10 · ₺980.00` for a mixed-currency
 * ledger instead of summing unlike currencies into a meaningless number.
 */
data class CurrencyTotal(
    val currency: String,
    val totalMinor: Long,
    val count: Int,
)

/** Total spend rolled up by on-device category — feeds the breakdown chart. */
data class CategoryTotal(
    val category: String,
    val currency: String,
    val totalMinor: Long,
    val count: Int,
)

/** Data-access object for the receipts ledger. */
@Dao
interface ReceiptDao {

    /** Newest receipts first — drives the home list. Reactive via [Flow]. */
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReceiptEntity>>

    /**
     * Filtered, searchable ledger stream. [query] matches the merchant name or
     * the stored raw OCR text (case-insensitive `LIKE`), which is exactly why
     * `rawText` is persisted. An empty [query] matches everything; a null
     * [category] disables the category filter. This is what makes the
     * "searchable local ledger" pitch true.
     */
    @Query(
        """
        SELECT * FROM receipts
        WHERE (:query = '' OR merchant LIKE '%' || :query || '%'
               OR rawText LIKE '%' || :query || '%')
          AND (:category IS NULL OR category = :category)
        ORDER BY createdAt DESC
        """,
    )
    fun search(query: String, category: String?): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun findById(id: Long): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observeById(id: Long): Flow<ReceiptEntity?>

    /** Reactive sum of every receipt total in a given currency (minor units). */
    @Query("SELECT COALESCE(SUM(totalMinor), 0) FROM receipts WHERE currency = :currency")
    fun observeTotalMinor(currency: String): Flow<Long>

    /**
     * Per-currency running totals, largest first. Replaces the old
     * single-currency sum so a ledger mixing USD/TRY/EUR is grouped correctly.
     */
    @Query(
        """
        SELECT currency AS currency,
               SUM(totalMinor) AS totalMinor,
               COUNT(*) AS count
        FROM receipts
        GROUP BY currency
        ORDER BY totalMinor DESC
        """,
    )
    fun observeCurrencyTotals(): Flow<List<CurrencyTotal>>

    /** Spend rolled up by category (and currency, so totals stay honest). */
    @Query(
        """
        SELECT category AS category,
               currency AS currency,
               SUM(totalMinor) AS totalMinor,
               COUNT(*) AS count
        FROM receipts
        GROUP BY category, currency
        ORDER BY totalMinor DESC
        """,
    )
    fun observeCategoryTotals(): Flow<List<CategoryTotal>>

    @Query("SELECT COUNT(*) FROM receipts")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(receipt: ReceiptEntity): Long

    @Update
    suspend fun update(receipt: ReceiptEntity)

    @Delete
    suspend fun delete(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
