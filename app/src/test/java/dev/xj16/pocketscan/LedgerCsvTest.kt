package dev.xj16.pocketscan

import dev.xj16.pocketscan.data.ReceiptEntity
import dev.xj16.pocketscan.util.LedgerCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure-JVM tests for the CSV export serializer. */
class LedgerCsvTest {

    private fun receipt(
        merchant: String,
        totalMinor: Long,
        currency: String,
        category: String = "Other",
        epochDay: Long? = LocalDate.of(2026, 3, 14).toEpochDay(),
    ) = ReceiptEntity(
        merchant = merchant,
        purchaseEpochDay = epochDay,
        totalMinor = totalMinor,
        currency = currency,
        category = category,
        imagePath = null,
        rawText = "",
        createdAt = 1_700_000_000_000,
    )

    @Test
    fun `exports a header and one row per receipt`() {
        val csv = LedgerCsv.export(
            listOf(
                receipt("Whole Foods", 1058, "USD", "Groceries"),
                receipt("BIM Market", 8980, "TRY", "Groceries"),
            ),
        )
        val lines = csv.trim().split("\r\n")
        assertEquals(3, lines.size) // header + 2 rows
        assertEquals("date,merchant,category,total,currency,created_at_epoch_ms", lines[0])
        assertTrue(lines[1].startsWith("2026-03-14,Whole Foods,Groceries,10.58,USD,"))
        assertTrue(lines[2].contains("89.80,TRY,"))
    }

    @Test
    fun `quotes fields containing commas or quotes`() {
        val csv = LedgerCsv.export(listOf(receipt("Bob's, \"Diner\"", 500, "USD")))
        // Comma and quote force quoting; inner quotes are doubled.
        assertTrue(csv.contains("\"Bob's, \"\"Diner\"\"\""))
    }

    @Test
    fun `minorToPlain formats major units with two decimals`() {
        assertEquals("10.58", LedgerCsv.minorToPlain(1058))
        assertEquals("0.05", LedgerCsv.minorToPlain(5))
        assertEquals("1000.00", LedgerCsv.minorToPlain(100000))
    }

    @Test
    fun `blank date is emitted as an empty field`() {
        val csv = LedgerCsv.export(listOf(receipt("No Date", 100, "USD", epochDay = null)))
        val row = csv.trim().split("\r\n")[1]
        assertTrue(row.startsWith(",No Date,"))
    }

    @Test
    fun `empty ledger still emits the header`() {
        val csv = LedgerCsv.export(emptyList())
        assertEquals("date,merchant,category,total,currency,created_at_epoch_ms", csv.trim())
    }
}
