package dev.xj16.pocketscan.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Data-access object for the receipts ledger. */
@Dao
interface ReceiptDao {

    /** Newest receipts first — drives the home list. Reactive via [Flow]. */
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun findById(id: Long): ReceiptEntity?

    /** Reactive sum of every receipt total in a given currency (minor units). */
    @Query("SELECT COALESCE(SUM(totalMinor), 0) FROM receipts WHERE currency = :currency")
    fun observeTotalMinor(currency: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM receipts")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(receipt: ReceiptEntity): Long

    @Update
    suspend fun update(receipt: ReceiptEntity)

    @Delete
    suspend fun delete(receipt: ReceiptEntity)
}
