package dev.xj16.pocketscan.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.xj16.pocketscan.data.ReceiptEntity
import java.io.File

/**
 * Writes the ledger to a CSV in the app cache and returns a share [Intent]
 * backed by a [FileProvider] URI. The pure serialization lives in [LedgerCsv];
 * this only handles the Android file + intent plumbing.
 */
object LedgerExporter {

    fun exportIntent(context: Context, receipts: List<ReceiptEntity>): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "pocketscan-ledger.csv")
        file.writeText(LedgerCsv.export(receipts))

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PocketScan ledger export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
