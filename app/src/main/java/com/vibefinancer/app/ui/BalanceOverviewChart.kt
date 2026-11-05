package com.vibefinancer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.vibefinancer.app.data.DetailedAccountDto
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun accountColor(hex: String?, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

private fun compactMoney(value: Double): String =
    if (abs(value) >= 1000) "%.1fK".format(value / 1000) else value.roundToInt().toString()

/** End-of-day balance per account across the selected month, like the web overview. */
@Composable
fun BalanceOverviewChart(accounts: List<DetailedAccountDto>, modifier: Modifier = Modifier) {
    val fallback = MaterialTheme.colorScheme.primary
    val series = accounts.filter { it.chartData.size > 1 }
    if (series.isEmpty()) {
        Text(
            "Add transactions to see balances over the month.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val values = series.flatMap { acc -> acc.chartData.map { it.balance } } + 0.0
    val maxV = values.max()
    val minV = values.min()
    val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
    val longest = series.maxBy { it.chartData.size }.chartData
    val count = longest.size

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val left = 40.dp.toPx()
        val bottom = 22.dp.toPx()
        val top = 8.dp.toPx()
        val plotWidth = size.width - left
        val plotHeight = size.height - top - bottom
        fun yOf(v: Double): Float = (top + (1 - (v - minV) / span) * plotHeight).toFloat()
        fun xOf(i: Int): Float = left + (if (count > 1) i * plotWidth / (count - 1) else 0f)

        listOf(maxV, (maxV + minV) / 2, minV).forEach { v ->
            drawLine(gridColor, Offset(left, yOf(v)), Offset(size.width, yOf(v)), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(compactMoney(v), labelStyle)
            drawText(layout, topLeft = Offset(0f, yOf(v) - layout.size.height / 2f))
        }

        series.forEach { acc ->
            val path = Path()
            acc.chartData.forEachIndexed { i, point ->
                if (i == 0) path.moveTo(xOf(i), yOf(point.balance)) else path.lineTo(xOf(i), yOf(point.balance))
            }
            drawPath(
                path,
                accountColor(acc.color, fallback),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        for (i in 0 until count) {
            val day = i + 1
            if (day == 1 || day == 5 || day == 15 || day == 25) {
                val layout = measurer.measure(day.toString(), labelStyle)
                drawText(layout, topLeft = Offset(xOf(i) - layout.size.width / 2f, size.height - bottom + 4.dp.toPx()))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountLegend(accounts: List<DetailedAccountDto>) {
    val fallback = MaterialTheme.colorScheme.primary
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        accounts.forEach { acc ->
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(accountColor(acc.color, fallback), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${acc.name} · ${formatMoney(acc.currentBalance, acc.currencyCode, acc.currencySymbol)}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
