package com.winterarc.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.theme.WinterArcColors
import kotlin.math.roundToInt

/**
 * Charts are drawn directly on a Compose canvas rather than through a charting library.
 *
 * The set of charts needed here is small and fixed, the palette is bespoke, and every
 * library considered would have added a dependency, a theming fight, and a release-build
 * risk — for four chart types that are roughly a hundred lines each.
 */

data class ChartPoint(val label: String, val value: Double)

/** A line chart with a filled gradient beneath it. Values are plotted against index order. */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = WinterArcColors.Gold,
    valueFormatter: (Double) -> String = { ((it * 10).roundToInt() / 10.0).toString() },
) {
    if (points.size < 2) {
        EmptyChart(
            modifier,
            if (points.isEmpty()) "No data yet" else "One data point — log another to see a trend",
        )
        return
    }

    val values = points.map { it.value }
    val min = values.min()
    val max = values.max()
    // A flat series would divide by zero; give it a nominal band so the line renders centred.
    val range = (max - min).takeIf { it > 0.0001 } ?: 1.0

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                valueFormatter(max),
                style = MaterialTheme.typography.labelSmall,
                color = WinterArcColors.Faint,
            )
            Text(
                valueFormatter(min),
                style = MaterialTheme.typography.labelSmall,
                color = WinterArcColors.Faint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
            val padY = 12f

            fun yFor(v: Double): Float =
                (size.height - padY) - (((v - min) / range).toFloat() * (size.height - padY * 2))

            val line = Path().apply {
                points.forEachIndexed { i, p ->
                    val x = i * stepX
                    val y = yFor(p.value)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo((points.size - 1) * stepX, size.height)
                lineTo(0f, size.height)
                close()
            }

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                ),
            )
            drawPath(path = line, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

            points.forEachIndexed { i, p ->
                drawCircle(
                    color = lineColor,
                    radius = 5f,
                    center = Offset(i * stepX, yFor(p.value)),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                points.first().label,
                style = MaterialTheme.typography.labelSmall,
                color = WinterArcColors.Faint,
            )
            Text(
                points.last().label,
                style = MaterialTheme.typography.labelSmall,
                color = WinterArcColors.Faint,
            )
        }
    }
}

/** Vertical bars — used for weekly and monthly volume, where discrete periods are the point. */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    barColor: Color = WinterArcColors.Gold,
) {
    if (points.isEmpty()) {
        EmptyChart(modifier, "No data yet")
        return
    }
    val max = points.maxOf { it.value }.takeIf { it > 0 } ?: 1.0

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            val gap = 8f
            val barWidth = ((size.width - gap * (points.size - 1)) / points.size).coerceAtLeast(2f)
            points.forEachIndexed { i, p ->
                val h = ((p.value / max).toFloat() * size.height).coerceAtLeast(2f)
                drawRoundRect(
                    color = if (i == points.lastIndex) barColor else barColor.copy(alpha = 0.55f),
                    topLeft = Offset(i * (barWidth + gap), size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                points.first().label,
                style = MaterialTheme.typography.labelSmall,
                color = WinterArcColors.Faint,
            )
            Text(
                points.last().label,
                style = MaterialTheme.typography.labelSmall,
                color = WinterArcColors.Faint,
            )
        }
    }
}

data class DonutSlice(val label: String, val value: Double, val color: Color)

/**
 * Donut chart.
 *
 * Only valid where the slices are parts of one whole measured in one unit — here, training
 * volume by muscle group. It is deliberately NOT used for unrelated progress rates, which
 * do not sum to anything and are shown as [ProgressRing]s instead.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    centerLabel: String = "",
    centerValue: String = "",
) {
    val total = slices.sumOf { it.value }
    if (slices.isEmpty() || total <= 0.0) {
        EmptyChart(modifier, "No volume recorded yet")
        return
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(148.dp)) {
                val stroke = 30f
                val inset = stroke / 2
                var start = -90f
                slices.forEach { slice ->
                    val sweep = ((slice.value / total) * 360.0).toFloat()
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = sweep - 1.5f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    centerValue,
                    style = MaterialTheme.typography.titleLarge,
                    color = WinterArcColors.GoldBright,
                )
                Text(
                    centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = WinterArcColors.Muted,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            slices.take(7).forEach { slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(slice.color),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.White,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${((slice.value / total) * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = WinterArcColors.Muted,
                    )
                }
            }
        }
    }
}

/**
 * A single goal's completion, drawn as its own ring.
 *
 * Separate rings rather than pie slices, because two independent goals at 50% each are not
 * two halves of one thing — combining them into a circle would assert a total that does not
 * exist.
 */
@Composable
fun ProgressRing(
    label: String,
    fraction: Float,
    caption: String,
    modifier: Modifier = Modifier,
    color: Color = WinterArcColors.Gold,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(96.dp)) {
                val stroke = 12f
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = WinterArcColors.NightBorder,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(
                "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = WinterArcColors.White,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = WinterArcColors.Faint,
        )
    }
}

@Composable
private fun EmptyChart(modifier: Modifier = Modifier, message: String) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = WinterArcColors.Faint)
    }
}

/** Palette for categorical slices — ordered so adjacent slices always contrast. */
val chartPalette: List<Color> = listOf(
    WinterArcColors.Gold,
    WinterArcColors.NightBlueBright,
    WinterArcColors.Success,
    WinterArcColors.GoldBright,
    Color(0xFF7E9BB5),
    Color(0xFFB98A4B),
    Color(0xFF4E8C6E),
    Color(0xFFD9A0A0),
    Color(0xFF8FA37A),
)
