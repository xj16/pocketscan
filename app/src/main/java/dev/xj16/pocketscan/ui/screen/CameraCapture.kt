package dev.xj16.pocketscan.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import dev.xj16.pocketscan.vision.DocumentScanner
import dev.xj16.pocketscan.vision.Quad
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A detected document, mapped into the coordinate space of the preview the user
 * sees. [quad] corners are already normalized to preview pixels; [detected] is
 * false when no document-like quad was found this frame.
 */
data class LiveDetection(
    val quad: Quad?,
    val detected: Boolean,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

/**
 * CameraX helper for the scanner screen. Owns three use cases:
 *  - a [Preview] bound to the on-screen [PreviewView],
 *  - an [ImageCapture] for the high-quality still, and
 *  - an [ImageAnalysis] stream that runs OpenCV edge detection on downscaled
 *    frames off the main thread and reports the detected quad back for the live
 *    overlay. This is what makes the advertised "live camera scanning with edge
 *    detection" actually visible on screen.
 */
class CameraCapture(
    private val context: Context,
    private val scanner: DocumentScanner = DocumentScanner(),
) {

    private val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    // Single background thread for analysis so frames are processed serially and
    // we never pile up OpenCV work.
    private val analysisExecutor: Executor = Executors.newSingleThreadExecutor()
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    private val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    // Throttle analysis to ~6 fps: OpenCV contour work is cheap but there's no
    // point running it faster than the overlay animates, and it keeps the
    // camera thread cool.
    @Volatile private var lastAnalyzedAt = 0L
    private val minIntervalMs = 160L

    /** Binds preview + capture + analysis; [onDetection] fires per analyzed frame. */
    fun bind(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: PreviewView,
        onDetection: (LiveDetection) -> Unit,
    ) {
        imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
            analyze(proxy, onDetection)
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                imageAnalysis,
            )
        }, mainExecutor)
    }

    private fun analyze(proxy: ImageProxy, onDetection: (LiveDetection) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAt < minIntervalMs) {
            proxy.close()
            return
        }
        lastAnalyzedAt = now
        try {
            val bitmap = proxy.toBitmapOrNull()
            if (bitmap == null) {
                onDetection(LiveDetection(null, false, 0, 0))
                return
            }
            val rotation = proxy.imageInfo.rotationDegrees
            val upright = if (rotation == 0) bitmap else bitmap.rotate(rotation)
            val quad = scanner.detectQuad(upright)
            onDetection(
                LiveDetection(
                    quad = quad,
                    detected = quad != null,
                    sourceWidth = upright.width,
                    sourceHeight = upright.height,
                ),
            )
        } catch (_: Throwable) {
            onDetection(LiveDetection(null, false, 0, 0))
        } finally {
            proxy.close()
        }
    }

    /** Captures a single frame and decodes it into an upright [Bitmap]. */
    suspend fun capture(): Bitmap = suspendCancellableCoroutine { cont ->
        imageCapture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        cont.resume(image.toUprightBitmap())
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    cont.resumeWithException(exc)
                }
            },
        )
    }
}

/** Decodes a JPEG [ImageProxy] and rotates it upright per its EXIF rotation. */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded
    return decoded.rotate(rotation)
}

/**
 * Converts an analysis [ImageProxy] (YUV_420_888 by default) into an RGB
 * [Bitmap] using the built-in helper. Returns null if the frame can't be
 * converted, which the analyzer treats as "no detection this frame".
 */
private fun ImageProxy.toBitmapOrNull(): Bitmap? =
    runCatching { toBitmap() }.getOrNull()

private fun Bitmap.rotate(degrees: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
