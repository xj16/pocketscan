package dev.xj16.pocketscan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.xj16.pocketscan.data.ReceiptEntity
import dev.xj16.pocketscan.data.ReceiptRepository
import dev.xj16.pocketscan.util.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the receipt-detail destination reached from a home-list tap. Streams
 * the single receipt reactively so an edit or delete elsewhere is reflected,
 * and owns the delete action (which also removes the cached scan image).
 */
class ReceiptDetailViewModel(
    private val repo: ReceiptRepository,
    private val receiptId: Long,
) : ViewModel() {

    val receipt: StateFlow<ReceiptEntity?> =
        repo.observe(receiptId)
            .map { it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /** Deletes the receipt and its stored scan image, then invokes [onDone]. */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.get(receiptId)?.let { entity ->
                entity.imagePath?.let { ImageStore.delete(it) }
                repo.remove(entity)
            }
            onDone()
        }
    }

    class Factory(
        private val repo: ReceiptRepository,
        private val receiptId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReceiptDetailViewModel(repo, receiptId) as T
    }
}
