package dev.xj16.pocketscan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.xj16.pocketscan.data.CategoryTotal
import dev.xj16.pocketscan.data.CurrencyTotal
import dev.xj16.pocketscan.data.ReceiptEntity
import dev.xj16.pocketscan.data.ReceiptRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** A category's share of spending, ready to render as a chart slice + legend row. */
data class CategorySlice(
    val category: String,
    val totalMinor: Long,
    val count: Int,
    val fraction: Float,
)

data class HomeUiState(
    val receipts: List<ReceiptEntity> = emptyList(),
    val count: Int = 0,
    /** Per-currency running totals, so mixed-currency ledgers stay honest. */
    val currencyTotals: List<CurrencyTotal> = emptyList(),
    /** Spend by category (in the ledger's dominant currency) for the chart. */
    val categorySlices: List<CategorySlice> = emptyList(),
    val chartCurrency: String? = null,
    val query: String = "",
    val categoryFilter: String? = null,
    val loading: Boolean = true,
) {
    /** True when a search or category filter is narrowing the list. */
    val isFiltering: Boolean get() = query.isNotBlank() || categoryFilter != null
}

/**
 * Backs the home screen. Streams:
 *  - a searchable/category-filtered ledger (search box + chips),
 *  - honest per-currency running totals (no more summing USD+TRY into one
 *    meaningless number), and
 *  - a spending-by-category breakdown for the on-screen chart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repo: ReceiptRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    // Debounced search text so we don't hit Room on every keystroke.
    private val filteredReceipts =
        combine(
            _query.debounce(180).distinctUntilChanged(),
            _category,
        ) { q, cat -> q.trim() to cat }
            .flatMapLatest { (q, cat) -> repo.search(q, cat) }

    val state: StateFlow<HomeUiState> =
        combine(
            filteredReceipts,
            repo.count,
            repo.currencyTotals,
            repo.categoryTotals,
            combine(_query, _category) { q, c -> q to c },
        ) { receipts, count, currencyTotals, categoryTotals, (q, cat) ->
            val slices = buildSlices(categoryTotals)
            HomeUiState(
                receipts = receipts,
                count = count,
                currencyTotals = currencyTotals,
                categorySlices = slices.slices,
                chartCurrency = slices.currency,
                query = q,
                categoryFilter = cat,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun onQueryChange(text: String) { _query.value = text }

    fun onCategorySelected(category: String?) {
        // Tapping the active chip clears the filter.
        _category.value = if (_category.value == category) null else category
    }

    fun clearFilters() {
        _query.value = ""
        _category.value = null
    }

    private data class Slices(val slices: List<CategorySlice>, val currency: String?)

    /**
     * Builds category slices from the dominant currency only. Mixing currencies
     * in one pie would be as wrong as summing them, so we chart the currency
     * with the most spend and label it clearly; the per-currency header still
     * reports every currency.
     */
    private fun buildSlices(totals: List<CategoryTotal>): Slices {
        if (totals.isEmpty()) return Slices(emptyList(), null)
        val dominant = totals
            .groupBy { it.currency }
            .mapValues { (_, rows) -> rows.sumOf { it.totalMinor } }
            .maxByOrNull { it.value }
            ?.key ?: return Slices(emptyList(), null)

        val rows = totals
            .filter { it.currency == dominant }
            .groupBy { it.category }
            .map { (category, group) ->
                Triple(category, group.sumOf { it.totalMinor }, group.sumOf { it.count })
            }
            .sortedByDescending { it.second }

        val grand = rows.sumOf { it.second }.coerceAtLeast(1L)
        val slices = rows.map { (category, minor, cnt) ->
            CategorySlice(
                category = category,
                totalMinor = minor,
                count = cnt,
                fraction = (minor.toDouble() / grand).toFloat(),
            )
        }
        return Slices(slices, dominant)
    }

    class Factory(private val repo: ReceiptRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repo) as T
    }
}
