@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.xj16.pocketscan.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xj16.pocketscan.data.CurrencyTotal
import dev.xj16.pocketscan.data.ReceiptEntity
import dev.xj16.pocketscan.ml.SpendCategory
import dev.xj16.pocketscan.ocr.ReceiptParser
import dev.xj16.pocketscan.ui.HomeUiState
import dev.xj16.pocketscan.ui.HomeViewModel
import java.time.LocalDate

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
    onReceiptClick: (Long) -> Unit,
    onExportClick: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PocketScan") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    if (state.count > 0) {
                        IconButton(onClick = onExportClick) {
                            Icon(
                                Icons.Filled.FileDownload,
                                contentDescription = "Export ledger as CSV",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    IconButton(onClick = onImportClick) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = "Import from gallery",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                text = { Text("Scan") },
            )
        },
    ) { padding ->
        val empty = state.receipts.isEmpty() && !state.isFiltering
        if (empty && !state.loading) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") {
                    SummaryHeader(state)
                }
                item(key = "search") {
                    SearchAndFilters(
                        query = state.query,
                        activeCategory = state.categoryFilter,
                        onQueryChange = viewModel::onQueryChange,
                        onCategory = viewModel::onCategorySelected,
                    )
                }
                if (state.receipts.isEmpty()) {
                    item(key = "no-results") { NoResults() }
                } else {
                    items(state.receipts, key = { it.id }) { receipt ->
                        ReceiptRow(
                            receipt = receipt,
                            onClick = { onReceiptClick(receipt.id) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(state: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Tracked total",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
            CurrencyTotalsRow(state.currencyTotals)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${state.count} receipt${if (state.count == 1) "" else "s"} in your ledger",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.categorySlices.isNotEmpty() && state.chartCurrency != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Spending by category",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(10.dp))
                SpendingDonut(
                    slices = state.categorySlices,
                    currency = state.chartCurrency,
                )
            }
        }
    }
}

/**
 * Honest per-currency totals: `$142.10 · ₺980.00`. Falls back to a single zero
 * when the ledger is empty. Replaces the old bug where every currency was
 * summed into one meaningless USD number.
 */
@Composable
private fun CurrencyTotalsRow(totals: List<CurrencyTotal>) {
    if (totals.isEmpty()) {
        Text(
            text = ReceiptParser.formatMoney(0, "USD"),
            style = MaterialTheme.typography.headlineMedium,
        )
        return
    }
    val label = totals.joinToString("   ·   ") { t ->
        ReceiptParser.formatMoney(t.totalMinor, t.currency)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun SearchAndFilters(
    query: String,
    activeCategory: String?,
    onQueryChange: (String) -> Unit,
    onCategory: (String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            placeholder = { Text("Search merchant or receipt text") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpendCategory.entries.forEach { category ->
                val selected = activeCategory == category.label
                FilterChip(
                    selected = selected,
                    onClick = { onCategory(category.label) },
                    label = { Text(category.label) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: ReceiptEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = receipt.merchant,
                    style = MaterialTheme.typography.titleMedium,
                )
                val dateLabel = receipt.purchaseEpochDay
                    ?.let { LocalDate.ofEpochDay(it).toString() }
                    ?: "No date"
                Text(
                    text = "$dateLabel · ${receipt.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = ReceiptParser.formatMoney(receipt.totalMinor, receipt.currency),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun NoResults() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No receipts match your search.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No receipts yet.\nTap Scan to capture your first one.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
