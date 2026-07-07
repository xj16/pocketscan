package dev.xj16.pocketscan

import dev.xj16.pocketscan.ocr.ReceiptParser
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A golden-corpus benchmark for the offline parser. It runs every hand-labeled
 * fixture in `resources/golden/corpus.tsv` through [ReceiptParser], scores
 * per-field accuracy, prints a report to the CI log, and fails if accuracy
 * regresses below a floor. Turns "robust heuristics" from a claim into a number.
 */
class GoldenCorpusTest {

    private data class Case(
        val id: String,
        val merchant: String,
        val date: String,
        val total: String,
        val currency: String,
        val raw: String,
    )

    private fun loadCorpus(): List<Case> {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden/corpus.tsv")
        assertNotNull("golden/corpus.tsv must be on the test classpath", stream)
        return stream!!.bufferedReader().useLines { lines ->
            lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val f = line.split("\t")
                    Case(
                        id = f[0],
                        merchant = f[1],
                        date = f[2],
                        total = f[3],
                        currency = f[4],
                        raw = f[5].replace("\\n", "\n"),
                    )
                }
                .toList()
        }
    }

    @Test
    fun `parser meets accuracy floor on the golden corpus`() {
        val cases = loadCorpus()
        assertTrue("corpus should not be empty", cases.isNotEmpty())

        var merchantOk = 0
        var dateOk = 0
        var totalOk = 0
        var currencyOk = 0
        val misses = StringBuilder()

        for (c in cases) {
            val p = ReceiptParser.parse(c.raw)

            val mOk = p.merchant == c.merchant
            val dOk = if (c.date == "-") p.date == null
                      else p.date == LocalDate.parse(c.date)
            val tOk = if (c.total == "-") p.totalMinor == null
                      else p.totalMinor == c.total.toLong()
            val cOk = if (c.currency == "-") p.currency == null
                      else p.currency == c.currency

            if (mOk) merchantOk++
            if (dOk) dateOk++
            if (tOk) totalOk++
            if (cOk) currencyOk++

            if (!(mOk && dOk && tOk && cOk)) {
                misses.appendLine("  [${c.id}] merchant=$mOk date=$dOk total=$tOk currency=$cOk")
            }
        }

        val n = cases.size
        val report = buildString {
            appendLine("── ReceiptParser golden-corpus accuracy ($n cases) ──")
            appendLine("  merchant : ${merchantOk}/$n  (${pct(merchantOk, n)}%)")
            appendLine("  date     : ${dateOk}/$n  (${pct(dateOk, n)}%)")
            appendLine("  total    : ${totalOk}/$n  (${pct(totalOk, n)}%)")
            appendLine("  currency : ${currencyOk}/$n  (${pct(currencyOk, n)}%)")
            if (misses.isNotEmpty()) append("misses:\n$misses")
        }
        println(report)

        // Accuracy floor: on this curated corpus the parser should be exact.
        // Loosened slightly below 100% would still be a regression signal.
        assertTrue("merchant accuracy regressed:\n$report", merchantOk == n)
        assertTrue("date accuracy regressed:\n$report", dateOk == n)
        assertTrue("total accuracy regressed:\n$report", totalOk == n)
        assertTrue("currency accuracy regressed:\n$report", currencyOk == n)
    }

    private fun pct(k: Int, n: Int): Int = if (n == 0) 0 else (k * 100) / n
}
