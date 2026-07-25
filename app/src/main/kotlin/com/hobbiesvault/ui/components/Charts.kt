package com.hobbiesvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class PieSlice(val label: String, val value: Float, val color: Color)

data class BarItem(val label: String, val value: Float, val color: Color)

/** Ponto de uma série temporal simples (x já normalizado no eixo do tempo, ex.: timestamp). */
data class LinePoint(val x: Float, val y: Float)

/** Gráfico de linha simples, sem eixos/legendas — para tendências (ex.: histórico de preço). */
@Composable
fun LineChartCanvas(
    points: List<LinePoint>,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    Canvas(modifier) {
        // Baseline
        drawLine(trackColor, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (points.size < 2) return@Canvas

        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val spanX = (maxX - minX).takeIf { it > 0f } ?: 1f
        val spanY = (maxY - minY).takeIf { it > 0f } ?: 1f

        fun toOffset(p: LinePoint): Offset {
            val nx = (p.x - minX) / spanX
            val ny = 1f - (p.y - minY) / spanY
            return Offset(nx * size.width, ny * size.height * 0.9f + size.height * 0.05f)
        }

        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, p ->
            val offset = toOffset(p)
            if (i == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = strokeWidth.toPx()))
    }
}

/** Donut chart simples desenhado com Canvas, sem dependência externa. */
@Composable
fun PieChartCanvas(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 26.dp,
    centerLabel: String? = null,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (total <= 0f) return@Canvas
            val strokePx = strokeWidth.toPx()
            val diameter = minOf(size.width, size.height) - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / total) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color      = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep.coerceAtLeast(0.5f),
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
                    )
                    startAngle += sweep
                }
            }
        }
        if (centerLabel != null) {
            Text(
                centerLabel,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Gráfico de barras verticais simples desenhado com Canvas, sem dependência externa. */
@Composable
fun BarChartCanvas(
    bars: List<BarItem>,
    modifier: Modifier = Modifier,
    barCornerRadius: androidx.compose.ui.unit.Dp = 4.dp,
    barAreaHeight: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val maxValue = bars.maxOfOrNull { it.value } ?: 0f
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (bar.value > 0f) "${bar.value.toInt()}" else "",
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(4.dp))
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(barAreaHeight),
                ) {
                    val cornerPx = barCornerRadius.toPx()
                    // trilha de fundo
                    drawRoundRect(
                        color         = trackColor,
                        topLeft       = Offset.Zero,
                        size          = size,
                        cornerRadius  = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                    )
                    if (maxValue > 0f && bar.value > 0f) {
                        val fraction = bar.value / maxValue
                        val barHeight = size.height * fraction
                        drawRoundRect(
                            color         = bar.color,
                            topLeft       = Offset(0f, size.height - barHeight),
                            size          = Size(size.width, barHeight),
                            cornerRadius  = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(bar.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
