package dev.xj16.pocketscan.vision

import android.content.Context
import android.graphics.Bitmap
import dev.xj16.pocketscan.ml.CategoryClassifier
import dev.xj16.pocketscan.ml.SpendCategory
import dev.xj16.pocketscan.ocr.ParsedReceipt
import dev.xj16.pocketscan.ocr.ReceiptParser
import dev.xj16.pocketscan.ocr.TextRecognizer
import dev.xj16.pocketscan.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the full offline capture-to-fields pipeline:
 *
 *   raw photo → [DocumentScanner] (edge detect + perspective warp + enhance)
 *             → save cropped image → [TextRecognizer] (on-device OCR)
 *             → [ReceiptParser] (extract merchant/date/total/currency)
 *             → [CategoryClassifier] (TFLite / keyword spending category)
 *
 * Everything runs on [Dispatchers.Default]/IO; nothing touches the network.
 */
class ScanPipeline(
    private val scanner: DocumentScanner = DocumentScanner(),
    private val recognizer: TextRecognizer = TextRecognizer(),
) {

    // Lazily built from the first Context we see; the TFLite model (if any)
    // lives in assets, so we need a context to open it.
    private var classifier: CategoryClassifier? = null

    data class Output(
        val croppedImagePath: String,
        val rawText: String,
        val parsed: ParsedReceipt,
        val category: SpendCategory,
        val edgesDetected: Boolean,
    )

    suspend fun process(context: Context, photo: Bitmap): Output {
        val scan = withContext(Dispatchers.Default) { scanner.scan(photo) }
        val path = withContext(Dispatchers.IO) { ImageStore.save(context, scan.bitmap) }
        val rawText = recognizer.recognize(scan.bitmap)
        val parsed = withContext(Dispatchers.Default) { ReceiptParser.parse(rawText) }
        val category = withContext(Dispatchers.Default) {
            val clf = classifier ?: CategoryClassifier.create(context.applicationContext)
                .also { classifier = it }
            clf.classify(rawText)
        }
        return Output(
            croppedImagePath = path,
            rawText = rawText,
            parsed = parsed,
            category = category,
            edgesDetected = scan.detected,
        )
    }

    fun close() {
        recognizer.close()
        classifier?.close()
    }
}
