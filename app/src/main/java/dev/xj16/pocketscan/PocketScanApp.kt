package dev.xj16.pocketscan

import android.app.Application
import dev.xj16.pocketscan.data.LedgerDatabase
import dev.xj16.pocketscan.data.ReceiptRepository
import dev.xj16.pocketscan.vision.OpenCvLoader

/**
 * Application entry point. Owns the process-lifetime singletons (the Room
 * database and the repository built on top of it) so screens can grab them
 * without a DI framework — this app is small enough that manual wiring is
 * clearer than pulling in Hilt.
 */
class PocketScanApp : Application() {

    val database: LedgerDatabase by lazy { LedgerDatabase.build(this) }

    val repository: ReceiptRepository by lazy {
        ReceiptRepository(database.receiptDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize the OpenCV native runtime once, up front. If the native
        // library is unavailable the loader degrades gracefully and the
        // scanner falls back to the full frame instead of crashing.
        OpenCvLoader.init()
    }
}
