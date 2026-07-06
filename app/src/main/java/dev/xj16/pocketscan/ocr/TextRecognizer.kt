package dev.xj16.pocketscan.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR wrapper around ML Kit's Latin text recognizer.
 *
 * The recognition model is *bundled* into the APK (see the
 * `com.google.mlkit:text-recognition` dependency), so inference runs fully
 * offline — no Play Services download, no network round-trip, no data leaving
 * the device. That is the whole point of PocketScan.
 */
class TextRecognizer {

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Runs OCR on [bitmap] and returns the full recognized text block. */
    suspend fun recognize(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, /* rotationDegrees = */ 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
            cont.invokeOnCancellation {
                // ML Kit tasks are not directly cancelable; closing the client
                // happens in close(). Nothing to do per-request.
            }
        }

    fun close() = recognizer.close()
}
