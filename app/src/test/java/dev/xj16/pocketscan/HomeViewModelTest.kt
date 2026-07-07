package dev.xj16.pocketscan

import app.cash.turbine.test
import dev.xj16.pocketscan.data.ReceiptEntity
import dev.xj16.pocketscan.data.ReceiptRepository
import dev.xj16.pocketscan.ui.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the [HomeViewModel] Flow pipeline over a fake DAO: the mixed-currency
 * total fix (per-currency grouping), the searchable/filterable ledger, and the
 * spending-by-category breakdown that feeds the chart. No Android, no Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun receipt(
        id: Long,
        merchant: String,
        totalMinor: Long,
        currency: String,
        category: String = "Other",
        rawText: String = "",
        createdAt: Long = id,
    ) = ReceiptEntity(
        id = id,
        merchant = merchant,
        purchaseEpochDay = null,
        totalMinor = totalMinor,
        currency = currency,
        category = category,
        imagePath = null,
        rawText = rawText,
        createdAt = createdAt,
    )

    private lateinit var dao: FakeReceiptDao
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakeReceiptDao(
            listOf(
                receipt(1, "Whole Foods", 1058, "USD", "Groceries", "milk bread", createdAt = 10),
                receipt(2, "BIM Market", 8980, "TRY", "Groceries", "ekmek sut", createdAt = 20),
                receipt(3, "Shell", 5000, "USD", "Transport", "fuel petrol", createdAt = 30),
                receipt(4, "Café de Flore", 935, "EUR", "Dining", "espresso", createdAt = 40),
            ),
        )
        vm = HomeViewModel(ReceiptRepository(dao))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `groups totals per currency instead of summing unlike currencies`() = runTest(dispatcher) {
        vm.state.test {
            // Skip the initial loading state, advance the debounced search flow.
            awaitItem()
            advanceTimeBy(300)
            val s = expectMostRecentItem()

            val byCode = s.currencyTotals.associate { it.currency to it.totalMinor }
            assertEquals(6058L, byCode["USD"])   // 1058 + 5000, NOT mixed with TRY/EUR
            assertEquals(8980L, byCode["TRY"])
            assertEquals(935L, byCode["EUR"])
            assertEquals(3, s.currencyTotals.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search narrows the ledger by merchant and raw text`() = runTest(dispatcher) {
        vm.onQueryChange("shell")
        vm.state.test {
            awaitItem()
            advanceTimeBy(400)
            val s = expectMostRecentItem()
            assertEquals(1, s.receipts.size)
            assertEquals("Shell", s.receipts.first().merchant)
            assertTrue(s.isFiltering)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `raw-text search matches OCR content not just merchant`() = runTest(dispatcher) {
        vm.onQueryChange("espresso")
        vm.state.test {
            awaitItem()
            advanceTimeBy(400)
            val s = expectMostRecentItem()
            assertEquals(1, s.receipts.size)
            assertEquals("Café de Flore", s.receipts.first().merchant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category filter restricts to the chosen category`() = runTest(dispatcher) {
        vm.onCategorySelected("Groceries")
        vm.state.test {
            awaitItem()
            advanceTimeBy(400)
            val s = expectMostRecentItem()
            assertEquals(2, s.receipts.size)
            assertTrue(s.receipts.all { it.category == "Groceries" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category slices are computed from the dominant currency`() = runTest(dispatcher) {
        vm.state.test {
            awaitItem()
            advanceTimeBy(300)
            val s = expectMostRecentItem()
            // USD is dominant (6058 minor). Slices should sum to ~1.0 and be USD.
            assertEquals("USD", s.chartCurrency)
            assertNotNull(s.categorySlices.firstOrNull())
            val sum = s.categorySlices.sumOf { it.fraction.toDouble() }
            assertEquals(1.0, sum, 0.001)
            // Transport (5000) should outrank Groceries (1058) within USD.
            assertEquals("Transport", s.categorySlices.first().category)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping an active category chip clears the filter`() = runTest(dispatcher) {
        vm.onCategorySelected("Dining")
        assertEquals("Dining", vm.category.value)
        vm.onCategorySelected("Dining")
        assertEquals(null, vm.category.value)
    }
}
