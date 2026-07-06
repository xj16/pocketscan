package dev.xj16.pocketscan.ocr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * Structured fields extracted from raw receipt OCR text.
 *
 * [totalMinor] is in minor currency units (cents). Any field may be null/blank
 * when the heuristics can't find it — the UI lets the user correct it before
 * saving, so the parser aims for high-precision guesses over reckless ones.
 */
data class ParsedReceipt(
    val merchant: String?,
    val date: LocalDate?,
    val totalMinor: Long?,
    val currency: String?,
)

/**
 * Heuristic, offline receipt parser. No ML, no network — just robust regexes
 * and line-ranking that work across the messy real-world layouts OCR produces.
 *
 * It deliberately favors the *total* line over subtotals/taxes by keyword
 * ranking, and it understands both `1,234.56` (US/UK) and `1.234,56`
 * (EU/TR) decimal conventions.
 */
object ReceiptParser {

    /** Currency symbols/codes we recognize, mapped to ISO-4217. */
    private val CURRENCY_TOKENS: List<Pair<Regex, String>> = listOf(
        Regex("""₺|\bTL\b|\bTRY\b""", RegexOption.IGNORE_CASE) to "TRY",
        Regex("""€|\bEUR\b""", RegexOption.IGNORE_CASE) to "EUR",
        Regex("""£|\bGBP\b""", RegexOption.IGNORE_CASE) to "GBP",
        Regex("""\$|\bUSD\b""", RegexOption.IGNORE_CASE) to "USD",
    )

    /** Lines containing one of these words are strong "grand total" signals. */
    private val TOTAL_KEYWORDS = listOf(
        "grand total", "total due", "amount due", "balance due",
        "total", "toplam", "genel toplam", "tutar",
    )

    /** Lines we must NOT treat as the grand total. */
    private val NEGATIVE_KEYWORDS = listOf(
        "subtotal", "sub total", "ara toplam", "tax", "kdv", "vat",
        "change", "cash", "tip", "discount", "indirim",
    )

    // A monetary amount: optional thousands separators + a 2-digit fraction.
    private val AMOUNT = Regex("""(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})|\d+[.,]\d{2})""")

    fun parse(raw: String): ParsedReceipt {
        val lines = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedReceipt(
            merchant = guessMerchant(lines),
            date = guessDate(lines),
            totalMinor = guessTotalMinor(lines),
            currency = guessCurrency(raw),
        )
    }

    // --- merchant ------------------------------------------------------------

    /**
     * The merchant name is almost always in the first few lines and is the
     * "wordiest" of them (letters, not amounts). We pick the earliest line that
     * looks like a name rather than an address, phone, or price.
     */
    internal fun guessMerchant(lines: List<String>): String? {
        for (line in lines.take(6)) {
            val letters = line.count { it.isLetter() }
            val digits = line.count { it.isDigit() }
            if (letters < 3) continue
            if (digits > letters) continue // phone numbers, dates, receipt ids
            if (AMOUNT.containsMatchIn(line)) continue
            if (line.contains("receipt", ignoreCase = true)) continue
            return line.trim().trim('*', '-', '=', ' ')
        }
        return lines.firstOrNull()
    }

    // --- date ----------------------------------------------------------------

    private val DATE_PATTERNS: List<Pair<Regex, DateTimeFormatter>> = listOf(
        Regex("""\b(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\b""") to
            DateTimeFormatter.ofPattern("yyyy-M-d"),
        Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})\b""") to
            DateTimeFormatter.ofPattern("d-M-yyyy"),
        Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2})\b""") to
            DateTimeFormatter.ofPattern("d-M-yy"),
    )

    internal fun guessDate(lines: List<String>): LocalDate? {
        val text = lines.joinToString("\n")
        for ((pattern, formatter) in DATE_PATTERNS) {
            val m = pattern.find(text) ?: continue
            val normalized = m.value.replace(Regex("[/.]"), "-")
            return runCatching { LocalDate.parse(normalized, formatter) }.getOrNull()
                ?: continue
        }
        return null
    }

    // --- total ---------------------------------------------------------------

    internal fun guessTotalMinor(lines: List<String>): Long? {
        // 1) Prefer a line with a positive total keyword and no negative one.
        val keywordCandidates = lines.mapNotNull { line ->
            val lower = line.lowercase()
            if (NEGATIVE_KEYWORDS.any { lower.contains(it) }) return@mapNotNull null
            if (TOTAL_KEYWORDS.none { lower.contains(it) }) return@mapNotNull null
            lastAmountOnLine(line)
        }
        if (keywordCandidates.isNotEmpty()) return keywordCandidates.max()

        // 2) Fallback: the largest monetary amount anywhere is usually the total.
        val allAmounts = lines.flatMap { line -> allAmountsOnLine(line) }
        return allAmounts.maxOrNull()
    }

    private fun lastAmountOnLine(line: String): Long? =
        allAmountsOnLine(line).lastOrNull()

    private fun allAmountsOnLine(line: String): List<Long> =
        AMOUNT.findAll(line).mapNotNull { toMinor(it.value) }.toList()

    /**
     * Parses a monetary token into integer cents, handling both `1,234.56`
     * and `1.234,56` conventions by treating the *last* separator as the
     * decimal point.
     */
    internal fun toMinor(token: String): Long? {
        val t = token.trim()
        val lastSep = t.lastIndexOfAny(charArrayOf('.', ','))
        if (lastSep == -1) {
            val whole = t.toLongOrNull() ?: return null
            return whole * 100
        }
        val intPart = t.substring(0, lastSep).replace(Regex("[.,]"), "")
        val fracPart = t.substring(lastSep + 1)
        if (fracPart.length != 2) {
            // Not a 2-dp decimal (e.g. a thousands-only "1.234"): treat as whole.
            val whole = t.replace(Regex("[.,]"), "").toLongOrNull() ?: return null
            return whole * 100
        }
        val intVal = intPart.ifEmpty { "0" }.toLongOrNull() ?: return null
        val fracVal = fracPart.toLongOrNull() ?: return null
        return intVal * 100 + fracVal
    }

    // --- currency ------------------------------------------------------------

    internal fun guessCurrency(raw: String): String? {
        for ((pattern, code) in CURRENCY_TOKENS) {
            if (pattern.containsMatchIn(raw)) return code
        }
        return null
    }

    /** Formats minor units + ISO code as a display string, e.g. 1299,"USD" -> "$12.99". */
    fun formatMoney(minor: Long, currency: String): String {
        val symbol = when (currency.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "TRY" -> "₺"
            else -> "$currency "
        }
        val major = minor / 100
        val cents = (minor % 100).toString().padStart(2, '0')
        return "$symbol$major.$cents"
    }

    /** Rounds a floating major-unit amount to integer minor units. */
    fun majorToMinor(major: Double): Long = (major * 100).roundToLong()
}
