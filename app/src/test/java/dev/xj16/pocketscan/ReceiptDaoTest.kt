package dev.xj16.pocketscan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.xj16.pocketscan.data.LedgerDatabase
import dev.xj16.pocketscan.data.ReceiptDao
import dev.xj16.pocketscan.data.ReceiptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Round-trips the real Room [ReceiptDao] against an in-memory SQLite database,
 * exercising the actual generated SQL for insert/query/search/aggregate/delete.
 * Mirrors the iOS `LedgerStoreTests` so both platforms have DB-layer coverage,
 * and makes the README's "JVM/Robolectric" claim true for the data layer.
 */
@RunWith(RobolectricTestRunner::class)
class ReceiptDaoTest {

    private lateinit var db: LedgerDatabase
    private lateinit var dao: ReceiptDao

    private fun receipt(
        merchant: String,
        totalMinor: Long,
        currency: String,
        category: String = "Other",
        rawText: String = "",
        createdAt: Long = System.currentTimeMillis(),
    ) = ReceiptEntity(
        merchant = merchant,
        purchaseEpochDay = null,
        totalMinor = totalMinor,
        currency = currency,
        category = category,
        imagePath = null,
        rawText = rawText,
        createdAt = createdAt,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            LedgerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.receiptDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert then findById returns the stored receipt`() = runTest {
        val id = dao.insert(receipt("Whole Foods", 1058, "USD", "Groceries"))
        val found = dao.findById(id)
        assertEquals("Whole Foods", found?.merchant)
        assertEquals(1058, found?.totalMinor)
        assertEquals("USD", found?.currency)
    }

    @Test
    fun `observeAll returns newest first`() = runTest {
        dao.insert(receipt("Older", 100, "USD", createdAt = 1_000))
        dao.insert(receipt("Newer", 200, "USD", createdAt = 2_000))
        val all = dao.observeAll().first()
        assertEquals(listOf("Newer", "Older"), all.map { it.merchant })
    }

    @Test
    fun `search matches merchant and raw OCR text, case-insensitively`() = runTest {
        dao.insert(receipt("BIM Market", 8980, "TRY", rawText = "ekmek sut"))
        dao.insert(receipt("Shell", 5000, "USD", rawText = "fuel petrol"))

        assertEquals(1, dao.search("shell", null).first().size)
        assertEquals(1, dao.search("PETROL", null).first().size)   // raw text
        assertEquals(1, dao.search("bim", null).first().size)       // merchant
        assertEquals(2, dao.search("", null).first().size)          // empty = all
        assertTrue(dao.search("nonexistent", null).first().isEmpty())
    }

    @Test
    fun `search honors the category filter`() = runTest {
        dao.insert(receipt("A", 100, "USD", category = "Groceries"))
        dao.insert(receipt("B", 200, "USD", category = "Transport"))
        assertEquals(1, dao.search("", "Transport").first().size)
        assertEquals("B", dao.search("", "Transport").first().first().merchant)
    }

    @Test
    fun `currency totals group by ISO code, not summed together`() = runTest {
        dao.insert(receipt("US1", 1058, "USD"))
        dao.insert(receipt("US2", 5000, "USD"))
        dao.insert(receipt("TR1", 8980, "TRY"))

        val totals = dao.observeCurrencyTotals().first().associate { it.currency to it.totalMinor }
        assertEquals(6058L, totals["USD"])
        assertEquals(8980L, totals["TRY"])
    }

    @Test
    fun `category totals roll up by category and currency`() = runTest {
        dao.insert(receipt("A", 1000, "USD", category = "Groceries"))
        dao.insert(receipt("B", 500, "USD", category = "Groceries"))
        dao.insert(receipt("C", 300, "USD", category = "Dining"))

        val totals = dao.observeCategoryTotals().first()
        val groceries = totals.first { it.category == "Groceries" && it.currency == "USD" }
        assertEquals(1500L, groceries.totalMinor)
        assertEquals(2, groceries.count)
    }

    @Test
    fun `deleteById removes only the targeted receipt`() = runTest {
        val keep = dao.insert(receipt("Keep", 100, "USD"))
        val drop = dao.insert(receipt("Drop", 200, "USD"))
        dao.deleteById(drop)

        assertNull(dao.findById(drop))
        assertEquals("Keep", dao.findById(keep)?.merchant)
        assertEquals(1, dao.observeCount().first())
    }

    @Test
    fun `observeById reflects updates and deletes`() = runTest {
        val id = dao.insert(receipt("Original", 100, "USD"))
        assertEquals("Original", dao.observeById(id).first()?.merchant)

        dao.update(dao.findById(id)!!.copy(merchant = "Edited"))
        assertEquals("Edited", dao.observeById(id).first()?.merchant)

        dao.deleteById(id)
        assertNull(dao.observeById(id).first())
    }
}
