package dev.xj16.pocketscan

import dev.xj16.pocketscan.ocr.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the offline receipt parser. Pure JVM — no Android, no OpenCV,
 * no network — so they run fast in CI and lock in the parsing heuristics.
 */
class ReceiptParserTest {

    private val sampleUs = """
        WHOLE FOODS MARKET
        123 Main St, Austin TX
        Tel: (512) 555-0199

        Bananas            1.29
        Almond Milk        3.49
        Sourdough Loaf     4.99

        Subtotal           9.77
        Tax                0.81
        TOTAL             10.58

        VISA ************1234
        Date: 03/14/2026
    """.trimIndent()

    @Test
    fun `extracts merchant from first meaningful line`() {
        val parsed = ReceiptParser.parse(sampleUs)
        assertEquals("WHOLE FOODS MARKET", parsed.merchant)
    }

    @Test
    fun `picks the grand total over subtotal and tax`() {
        val parsed = ReceiptParser.parse(sampleUs)
        assertEquals(1058L, parsed.totalMinor) // $10.58 -> 1058 cents
    }

    @Test
    fun `parses US date format`() {
        val parsed = ReceiptParser.parse(sampleUs)
        assertEquals(LocalDate.of(2026, 3, 14), parsed.date)
    }

    @Test
    fun `detects USD currency from dollar amounts`() {
        val parsed = ReceiptParser.parse("Coffee \$4.50\nTOTAL \$4.50")
        assertEquals("USD", parsed.currency)
    }

    @Test
    fun `handles European decimal comma and TRY currency`() {
        val turkish = """
            BIM MARKET
            Ekmek            5,50
            Süt              32,90
            ARA TOPLAM       38,40
            KDV               3,49
            TOPLAM           41,89 TL
            Tarih: 14.03.2026
        """.trimIndent()
        val parsed = ReceiptParser.parse(turkish)
        assertEquals("BIM MARKET", parsed.merchant)
        assertEquals(4189L, parsed.totalMinor) // 41,89 -> 4189 cents
        assertEquals("TRY", parsed.currency)
        assertEquals(LocalDate.of(2026, 3, 14), parsed.date)
    }

    @Test
    fun `thousands separator with US convention parses correctly`() {
        // 1,234.56 -> 123456 cents
        assertEquals(123456L, ReceiptParser.toMinor("1,234.56"))
    }

    @Test
    fun `thousands separator with EU convention parses correctly`() {
        // 1.234,56 -> 123456 cents
        assertEquals(123456L, ReceiptParser.toMinor("1.234,56"))
    }

    @Test
    fun `bare integer amount becomes minor units`() {
        assertEquals(4200L, ReceiptParser.toMinor("42"))
    }

    @Test
    fun `thousands-only value without decimals is treated as whole`() {
        // "1.234" is 1234, not 12.34
        assertEquals(123400L, ReceiptParser.toMinor("1.234"))
    }

    @Test
    fun `falls back to largest amount when no total keyword`() {
        val noKeyword = """
            CORNER STORE
            Item A    2.00
            Item B    9.95
            Item C    1.50
        """.trimIndent()
        val parsed = ReceiptParser.parse(noKeyword)
        assertEquals(995L, parsed.totalMinor)
    }

    @Test
    fun `empty input yields nulls without throwing`() {
        val parsed = ReceiptParser.parse("")
        assertNull(parsed.totalMinor)
        assertNull(parsed.date)
        assertNull(parsed.currency)
    }

    @Test
    fun `formatMoney renders symbol and two decimals`() {
        assertEquals("$10.58", ReceiptParser.formatMoney(1058, "USD"))
        assertEquals("₺41.89", ReceiptParser.formatMoney(4189, "TRY"))
    }

    @Test
    fun `majorToMinor rounds correctly`() {
        assertEquals(1058L, ReceiptParser.majorToMinor(10.58))
        assertEquals(1000L, ReceiptParser.majorToMinor(10.0))
        // rounding half up
        assertEquals(1L, ReceiptParser.majorToMinor(0.005))
    }

    @Test
    fun `negative keywords never win as total`() {
        val parsed = ReceiptParser.parse(sampleUs)
        // Ensure the tax (0.81) and subtotal (9.77) are not chosen
        assertTrue(parsed.totalMinor != 81L)
        assertTrue(parsed.totalMinor != 977L)
    }
}
