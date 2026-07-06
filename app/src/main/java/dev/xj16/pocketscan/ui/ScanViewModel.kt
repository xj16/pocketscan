package dev.xj16.pocketscan.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.xj16.pocketscan.data.ReceiptEntity
import dev.xj16.pocketscan.data.ReceiptRepository
import dev.xj16.pocketscan.ocr.ReceiptParser
import dev.xj16.pocketscan.util.ImageStore
import dev.xj16.pocketscan.vision.ScanPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Editable form state shown on the review screen after a scan. */
data class ReviewForm(
    val merchant: String = "",
    val dateText: String = "",
    val totalText: String = "",
    val currency: String = "USD",
    val category: String = "Other",
    val rawText: String = "",
    val imagePath: String? = null,
    val edgesDetected: Boolean = true,
)

sealed interface ScanState {
    data object Idle : ScanState
    data object Processing : ScanState
    data class Review(val form: ReviewForm) : ScanState
    data object Saved : ScanState
    data class Error(val message: String) : ScanState
}

/**
 * Drives the capture → review → save flow. It runs the offline [ScanPipeline]
 * on a captured/imported bitmap, exposes an editable form, and commits the
 * confirmed receipt to the ledger.
 */
class ScanViewModel(
    private val repo: ReceiptRepository,
    private val pipeline: ScanPipeline = ScanPipeline(),
) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** Runs the full offline pipeline on a freshly captured/imported photo. */
    fun onPhotoCaptured(context: Context, photo: Bitmap) {
        _state.value = ScanState.Processing
        viewModelScope.launch {
            try {
                val out = pipeline.process(context.applicationContext, photo)
                val p = out.parsed
                _state.value = ScanState.Review(
                    ReviewForm(
                        merchant = p.merchant.orEmpty(),
                        dateText = (p.date ?: LocalDate.now()).toString(),
                        totalText = p.totalMinor?.let { minorToEditable(it) }.orEmpty(),
                        currency = p.currency ?: "USD",
                        category = out.category.label,
                        rawText = out.rawText,
                        imagePath = out.croppedImagePath,
                        edgesDetected = out.edgesDetected,
                    ),
                )
            } catch (t: Throwable) {
                _state.value = ScanState.Error(t.message ?: "Scan failed")
            }
        }
    }

    fun updateForm(transform: (ReviewForm) -> ReviewForm) {
        val current = _state.value
        if (current is ScanState.Review) {
            _state.value = ScanState.Review(transform(current.form))
        }
    }

    /** Validates and persists the reviewed receipt. Returns false if invalid. */
    fun save(): Boolean {
        val current = _state.value as? ScanState.Review ?: return false
        val form = current.form
        val totalMinor = parseEditableToMinor(form.totalText) ?: return false
        val epochDay = runCatching { LocalDate.parse(form.dateText).toEpochDay() }.getOrNull()

        viewModelScope.launch {
            repo.add(
                ReceiptEntity(
                    merchant = form.merchant.ifBlank { "Unknown merchant" },
                    purchaseEpochDay = epochDay,
                    totalMinor = totalMinor,
                    currency = form.currency.ifBlank { "USD" }.uppercase(),
                    category = form.category.ifBlank { "Other" },
                    imagePath = form.imagePath,
                    rawText = form.rawText,
                ),
            )
            _state.value = ScanState.Saved
        }
        return true
    }

    /** Discards the in-progress scan and its cached image. */
    fun discard() {
        (_state.value as? ScanState.Review)?.form?.imagePath?.let { ImageStore.delete(it) }
        _state.value = ScanState.Idle
    }

    fun reset() {
        _state.value = ScanState.Idle
    }

    override fun onCleared() {
        pipeline.close()
    }

    private fun minorToEditable(minor: Long): String {
        val major = minor / 100
        val cents = (minor % 100).toString().padStart(2, '0')
        return "$major.$cents"
    }

    private fun parseEditableToMinor(text: String): Long? {
        val cleaned = text.trim().replace(',', '.')
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value < 0) return null
        return ReceiptParser.majorToMinor(value)
    }

    class Factory(
        private val repo: ReceiptRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScanViewModel(repo) as T
    }
}
