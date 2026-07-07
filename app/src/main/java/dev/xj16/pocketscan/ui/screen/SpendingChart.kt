package dev.xj16.pocketscan.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xj16.pocketscan.ocr.ReceiptParser
import dev.xj16.pocketscan.ui.CategorySlice

/**
 * A dependency-free spending-by-category donut, drawn with a Compose [Canvas]
 * (no charting library). Each category gets a stable color; the arc sweeps are
 * animated in, and a legend lists the amounts. Rendered entirely from data the
 * app already computes in [dev.xj16.pocketscan.ui.HomeViewModel].
 */
@Composable
fun SpendingDonut(
    slices: List<CategorySlice>,
    currency: String,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) return

    // Reveal animation: 0f → 1f drives the total swept fraction.
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(700),
        label = "donutSweep",
    )

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(132.dp).padding(6.dp)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)

                var startAngle = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = slice.fraction * 360f * progress
                    drawArc(
                        color = categoryColor(index),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    startAngle += slice.fraction * 360f
                }
            }
            // Center label: the biggest category's share.
            val top = slices.first()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(top.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = top.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slices.take(6).forEachIndexed { index, slice ->
                LegendRow(
                    color = categoryColor(index),
                    label = slice.category,
                    amount = ReceiptParser.formatMoney(slice.totalMinor, currency),
                    percent = (slice.fraction * 100).toInt(),
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, amount: String, percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
    }
}

// A fixed, colorblind-friendly-ish palette. Stable per slice index so the same
// category keeps its color as amounts change.
private val CHART_COLORS = listOf(
    Color(0xFF2E7D6B), // teal
    Color(0xFF3D6FB4), // blue
    Color(0xFFB4791F), // amber
    Color(0xFF8A5CD0), // violet
    Color(0xFFC0553B), // clay
    Color(0xFF5B8C3E), // olive
    Color(0xFF9C9088), // stone (Other)
)

private fun categoryColor(index: Int): Color = CHART_COLORS[index % CHART_COLORS.size]
