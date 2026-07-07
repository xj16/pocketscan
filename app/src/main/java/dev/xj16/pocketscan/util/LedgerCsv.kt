package dev.xj16.pocketscan.util

import dev.xj16.pocketscan.data.ReceiptEntity
import java.time.LocalDate

/**
 * Pure, dependency-free CSV serialization of the ledger. Kept out of any
 * Android class so it is unit-testable on the JVM and reusable for both the
 * "share sheet" path and any future export target.
 *
 * The format is RFC-4180-ish: comma-separated, CRLF line endings, and any field
 * containing a comma, quote, or newline is wrapped in double quotes with inner
 * quotes doubled. A monetary value is emitted in major units with two decimals
 * (e.g. `10.58`) so spreadsheets read it as a number.
 */
object LedgerCsv {

    private val HEADER = listOf(
        "date", "merchant", "category", "total", "currency", "created_at_epoch_ms",
    )

    fun export(receipts: List<ReceiptEntity>): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",", transform = ::escape)).append("\r\n")
        for (r in receipts) {
            val row = listOf(
                r.purchaseEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "",
                r.merchant,
                r.category,
                minorToPlain(r.totalMinor),
                r.currency,
                r.createdAt.toString(),
            )
            sb.append(row.joinToString(",", transform = ::escape)).append("\r\n")
        }
        return sb.toString()
    }

    /** Minor units → plain `major.cc` string, no currency symbol, no grouping. */
    internal fun minorToPlain(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** Quotes a field iff it contains a comma, quote, CR or LF. */
    internal fun escape(field: String): String {
        val needsQuote = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }
}
