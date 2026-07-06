package dev.xj16.pocketscan.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xj16.pocketscan.ui.ReviewForm
import dev.xj16.pocketscan.ui.ScanState
import dev.xj16.pocketscan.ui.ScanViewModel

/**
 * Post-scan review: shows the corrected scan image, the parsed fields as
 * editable text inputs (merchant, date, total, currency), and the raw OCR
 * text. The user confirms and the receipt lands in the SQLite ledger.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ScanViewModel,
    onSaved: () -> Unit,
    onDiscard: () -> Unit,
) {
    val scanState by viewModel.state.collectAsStateWithLifecycle()
    // The screen only renders while a review form is in flight. Once the user
    // saves or discards, the ViewModel flips state and navigation (driven by
    // the button callbacks) has already taken us back home.
    val form = (scanState as? ScanState.Review)?.form ?: return

    Scaffold(
        topBar = { TopAppBar(title = { Text("Review receipt") }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScanPreview(form)

            if (!form.edgesDetected) {
                Text(
                    text = "Couldn't find document edges — used the full frame.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = form.merchant,
                onValueChange = { v -> viewModel.updateForm { it.copy(merchant = v) } },
                label = { Text("Merchant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.dateText,
                onValueChange = { v -> viewModel.updateForm { it.copy(dateText = v) } },
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.totalText,
                    onValueChange = { v -> viewModel.updateForm { it.copy(totalText = v) } },
                    label = { Text("Total") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = form.currency,
                    onValueChange = { v -> viewModel.updateForm { it.copy(currency = v) } },
                    label = { Text("Cur.") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = form.category,
                onValueChange = { v -> viewModel.updateForm { it.copy(category = v) } },
                label = { Text("Category (auto-detected on-device)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            RawTextCard(form.rawText)

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { if (viewModel.save()) onSaved() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save to ledger") }
            OutlinedButton(
                onClick = { viewModel.discard(); onDiscard() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Discard") }
        }
    }
}

@Composable
private fun ScanPreview(form: ReviewForm) {
    val bitmap = remember(form.imagePath) {
        form.imagePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Scanned receipt",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
        )
    }
}

@Composable
private fun RawTextCard(rawText: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Recognized text", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = rawText.ifBlank { "(no text recognized)" },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}
