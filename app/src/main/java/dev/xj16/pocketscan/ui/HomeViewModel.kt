package dev.xj16.pocketscan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.xj16.pocketscan.data.ReceiptEntity
import dev.xj16.pocketscan.data.ReceiptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val receipts: List<ReceiptEntity> = emptyList(),
    val count: Int = 0,
    val totalMinorUsd: Long = 0,
    val loading: Boolean = true,
)

/**
 * Backs the home screen: streams the ledger and a live running total. The
 * primary-currency total shown in the header is USD here; per-currency
 * breakdowns live on each row.
 */
class HomeViewModel(private val repo: ReceiptRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        combine(repo.receipts, repo.count, repo.totalMinor("USD")) { receipts, count, total ->
            HomeUiState(
                receipts = receipts,
                count = count,
                totalMinorUsd = total,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    class Factory(private val repo: ReceiptRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repo) as T
    }
}
