package dev.xj16.pocketscan.ui.screen

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.xj16.pocketscan.ui.ScanState
import dev.xj16.pocketscan.ui.ScanViewModel
import dev.xj16.pocketscan.vision.PointF2
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Live camera scanner. Handles the camera-permission gate, runs live edge
 * detection over the preview, draws the detected document quad as an animated
 * overlay, auto-captures once the document is stable, and hands the frame to
 * [ScanViewModel] for the offline pipeline.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScanViewModel,
    onReviewReady: () -> Unit,
    onBack: () -> Unit,
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val scanState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(scanState) {
        if (scanState is ScanState.Review) onReviewReady()
    }

    Box(Modifier.fillMaxSize()) {
        when {
            !cameraPermission.status.isGranted -> PermissionGate(
                onGrant = { cameraPermission.launchPermissionRequest() },
                onBack = onBack,
            )
            scanState is ScanState.Processing -> ProcessingOverlay()
            else -> CameraContent(viewModel = viewModel)
        }
    }
}

@Composable
private fun CameraContent(viewModel: ScanViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val capture = remember { CameraCapture(context) }
    val previewView = remember {
        // FIT_CENTER so the overlay's fit-center math matches exactly what the
        // user sees — no cropping mismatch between preview and quad.
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }

    var detection by remember { mutableStateOf<LiveDetection?>(null) }
    // Count consecutive stable detections to trigger a confident auto-capture.
    var stableFrames by remember { mutableStateOf(0) }
    var capturing by remember { mutableStateOf(false) }

    fun runCapture() {
        if (capturing) return
        capturing = true
        scope.launch {
            val bitmap = capture.capture()
            viewModel.onPhotoCaptured(context, bitmap)
        }
    }

    LaunchedEffect(Unit) {
        capture.bind(lifecycleOwner, previewView) { d ->
            detection = d
            stableFrames = if (d.detected) (stableFrames + 1).coerceAtMost(20) else 0
        }
    }

    // Auto-capture once the document has been held steady for a few frames.
    LaunchedEffect(stableFrames) {
        if (stableFrames >= AUTO_CAPTURE_FRAMES && !capturing) runCapture()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        QuadOverlay(detection = detection, modifier = Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            val locked = (detection?.detected == true)
            Surface(
                color = if (locked) LOCK_GREEN.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Text(
                    text = if (locked) "Document detected — hold steady" else "Align the receipt within the frame",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Button(
                onClick = { runCapture() },
                contentPadding = PaddingValues(20.dp),
            ) {
                Text("Capture")
            }
        }
    }
}

/**
 * Draws the detected [Quad] as an animated, highlighted polygon over the
 * preview. Corners are mapped from the analysis image into preview pixels with
 * the same fit-center transform [PreviewView] uses, so the outline sits on the
 * real document edges.
 */
@Composable
private fun QuadOverlay(detection: LiveDetection?, modifier: Modifier = Modifier) {
    val target = if (detection?.detected == true) 1f else 0f
    val alpha by animateFloatAsState(targetValue = target, label = "quadAlpha")

    Canvas(modifier = modifier) {
        val quad = detection?.quad ?: return@Canvas
        val srcW = detection.sourceWidth.toFloat()
        val srcH = detection.sourceHeight.toFloat()
        if (srcW <= 0f || srcH <= 0f || alpha <= 0.01f) return@Canvas

        // Fit-center: uniform scale, centered, letterboxed.
        val scale = min(size.width / srcW, size.height / srcH)
        val dispW = srcW * scale
        val dispH = srcH * scale
        val dx = (size.width - dispW) / 2f
        val dy = (size.height - dispH) / 2f

        fun map(p: PointF2) =
            Offset(dx + p.x.toFloat() * scale, dy + p.y.toFloat() * scale)

        val tl = map(quad.topLeft)
        val tr = map(quad.topRight)
        val br = map(quad.bottomRight)
        val bl = map(quad.bottomLeft)

        val path = Path().apply {
            moveTo(tl.x, tl.y)
            lineTo(tr.x, tr.y)
            lineTo(br.x, br.y)
            lineTo(bl.x, bl.y)
            close()
        }

        drawPath(path, color = LOCK_GREEN.copy(alpha = 0.18f * alpha))
        drawPath(
            path,
            color = LOCK_GREEN.copy(alpha = alpha),
            style = Stroke(width = 5.dp.toPx()),
        )
        // Corner accents.
        listOf(tl, tr, br, bl).forEach { c ->
            drawCircle(color = Color.White.copy(alpha = alpha), radius = 6.dp.toPx(), center = c)
            drawCircle(color = LOCK_GREEN.copy(alpha = alpha), radius = 4.dp.toPx(), center = c)
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(48.dp))
        Text(
            text = "Detecting edges and reading text…",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "PocketScan needs the camera to scan documents. " +
                "Nothing ever leaves your device.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("Grant camera access")
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
            Text("Go back")
        }
    }
}

// How many consecutive stable detections before we auto-fire the shutter.
private const val AUTO_CAPTURE_FRAMES = 8
private val LOCK_GREEN = Color(0xFF1F9E5A)
