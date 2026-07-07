package dev.xj16.pocketscan

import dev.xj16.pocketscan.data.ReceiptEntity
import dev.xj16.pocketscan.data.ReceiptRepository
import dev.xj16.pocketscan.ui.HomeUiState
import dev.xj16.pocketscan.ui.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 *
 * Each test keeps a live collector on `state` (via [backgroundScope]) so the
 * `WhileSubscribed`-sharing StateFlow is active, then drains the debounce and
 * upstream flows with [advanceUntilIdle] before asserting on the latest value.
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
    ) = ReceiptEntity(
        id = id,
        merchant = merchant,
        purchaseEpochDay = null,
        totalMinor = totalMinor,
        currency = currency,
        category = category,
        imagePath = null,
        rawText = rawText,
        createdAt = id,
    )

    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val dao = FakeReceiptDao(
            listOf(
                receipt(1, "Whole Foods", 1058, "USD", "Groceries", "milk bread"),
                receipt(2, "BIM Market", 8980, "TRY", "Groceries", "ekmek sut"),
                receipt(3, "Shell", 5000, "USD", "Transport", "fuel petrol"),
                receipt(4, "Cafe de Flore", 935, "EUR", "Dining", "espresso"),
            ),
        )
        vm = HomeViewModel(ReceiptRepository(dao))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Keeps `state` collected in the background and returns the settled value. */
    private suspend fun kotlinx.coroutines.test.TestScope.settled(): HomeUiState {
        val seen = mutableListOf<HomeUiState>()
        vm.state.onEach { seen.add(it) }.launchIn(backgroundScope)
        advanceUntilIdle()
        return seen.last()
    }

    @Test
    fun `groups totals per currency instead of summing unlike currencies`() = runTest(dispatcher) {
        val s = settled()
        val byCode = s.currencyTotals.associate { it.currency to it.totalMinor }
        assertEquals(6058L, byCode["USD"]) // 1058 + 5000, NOT mixed with TRY/EUR
        assertEquals(8980L, byCode["TRY"])
        assertEquals(935L, byCode["EUR"])
        assertEquals(3, s.currencyTotals.size)
    }

    @Test
    fun `search narrows the ledger by merchant`() = runTest(dispatcher) {
        vm.onQueryChange("shell")
        val s = settled()
        assertEquals(1, s.receipts.size)
        assertEquals("Shell", s.receipts.first().merchant)
        assertTrue(s.isFiltering)
    }

    @Test
    fun `raw-text search matches OCR content not just merchant`() = runTest(dispatcher) {
        vm.onQueryChange("espresso")
        val s = settled()
        assertEquals(1, s.receipts.size)
        assertEquals("Cafe de Flore", s.receipts.first().merchant)
    }

    @Test
    fun `category filter restricts to the chosen category`() = runTest(dispatcher) {
        vm.onCategorySelected("Groceries")
        val s = settled()
        assertEquals(2, s.receipts.size)
        assertTrue(s.receipts.all { it.category == "Groceries" })
    }

    @Test
    fun `category slices are computed from the dominant currency`() = runTest(dispatcher) {
        val s = settled()
        // USD is dominant (6058 minor). Slices should sum to ~1.0 and be USD.
        assertEquals("USD", s.chartCurrency)
        assertNotNull(s.categorySlices.firstOrNull())
        val sum = s.categorySlices.sumOf { it.fraction.toDouble() }
        assertEquals(1.0, sum, 0.001)
        // Transport (5000) should outrank Groceries (1058) within USD.
        assertEquals("Transport", s.categorySlices.first().category)
    }

    @Test
    fun `tapping an active category chip clears the filter`() {
        vm.onCategorySelected("Dining")
        assertEquals("Dining", vm.category.value)
        vm.onCategorySelected("Dining")
        assertEquals(null, vm.category.value)
    }
}
