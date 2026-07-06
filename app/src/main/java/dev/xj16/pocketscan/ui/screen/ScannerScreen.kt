package dev.xj16.pocketscan.ui.screen

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.xj16.pocketscan.ui.ScanState
import dev.xj16.pocketscan.ui.ScanViewModel
import kotlinx.coroutines.launch

/**
 * Live camera scanner. Handles the camera-permission gate, shows a framing
 * overlay, captures a frame, and hands it to [ScanViewModel] for the offline
 * pipeline. Navigates onward once a review form is ready.
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
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(Unit) { capture.bind(lifecycleOwner, previewView) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Framing hint + capture control.
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "Align the receipt within the frame",
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Button(
                onClick = {
                    scope.launch {
                        val bitmap = capture.capture()
                        viewModel.onPhotoCaptured(context, bitmap)
                    }
                },
                contentPadding = PaddingValues(20.dp),
            ) {
                Text("Capture")
            }
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
