package dev.xj16.pocketscan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The local SQLite ledger. A single table today, but versioned so future
 * schema changes ship real migrations rather than destructive rebuilds.
 */
@Database(
    entities = [ReceiptEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun receiptDao(): ReceiptDao

    companion object {
        private const val DB_NAME = "pocketscan.db"

        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LedgerDatabase::class.java,
                DB_NAME,
            )
                // Reads run on background dispatchers via Flow/suspend, so the
                // main-thread guard stays on to catch accidental blocking I/O.
                .build()
    }
}
