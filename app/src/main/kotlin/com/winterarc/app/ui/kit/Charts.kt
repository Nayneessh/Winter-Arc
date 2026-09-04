package com.winterarc.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.theme.W
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Charts are drawn directly on a Compose canvas.
 *
 * The set needed here is small, fixed and bespoke to this palette. Every charting library
 * considered would have brought a theming fight and a release-build risk for the sake of four
 * shapes, none of which is more than a hundred lines.
 *
 * Labels are laid out as real text composables around the canvas rather than drawn into it, so
 * they scale with the user's font settings instead of ignoring them.
 */

private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/**
 * A 270-degree gauge.
 *
 * Open at the bottom rather than closed into a full circle: it echoes the app's own arc mark,
 * and the gap gives the value underneath somewhere to sit.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = W.Gold,
    trackColor: Color = W.Line,
    strokeWidth: Dp = 10.dp,
    milestone: Float? = null,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown = if (animate) animateValue(progress.coerceIn(0f, 1f)) else progress.coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)

            drawArc(
                color = trackColor,
                startAngle = ARC_START,
                sweepAngle = ARC_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (shown > 0.001f) {
                // A wide, faint pass under the real stroke. This is the glow, and it is the
                // single detail that stops the ring reading as a flat progress bar bent round.
                drawArc(
                    color = color.copy(alpha = 0.18f),
                    startAngle = ARC_START,
                    sweepAngle = ARC_SWEEP * shown,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(color.copy(alpha = 0.55f), color, Color.White.copy(alpha = 0.85f), color),
                        center = Offset(size.width / 2f, size.height / 2f),
                    ),
                    startAngle = ARC_START,
                    sweepAngle = ARC_SWEEP * shown,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // The milestone: a notch across the track showing the checkpoint on the way.
            milestone?.let { m ->
                val angle = Math.toRadians((ARC_START + ARC_SWEEP * m.coerceIn(0f, 1f)).toDouble())
                val r = d / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val inner = r - stroke * 0.72f
                val outer = r + stroke * 0.72f
                drawLine(
                    color = W.Ink.copy(alpha = 0.55f),
                    start = Offset(cx + (cos(angle) * inner).toFloat(), cy + (sin(angle) * inner).toFloat()),
                    end = Offset(cx + (cos(angle) * outer).toFloat(), cy + (sin(angle) * outer).toFloat()),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }
        content()
    }
}

/** A ring with its figure inside and its name beneath -- the dashboard's goal unit. */
@Composable
fun GoalRing(
    label: String,
    value: String,
    unit: String,
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = W.Gold,
    milestone: Float? = null,
    footnote: String? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(
            progress = progress,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            color = color,
            strokeWidth = 9.dp,
            milestone = milestone,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = W.Ink,
                    maxLines = 1,
                )
                Text(unit, style = MaterialTheme.typography.labelSmall, color = W.Faint)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = W.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (footnote != null) {
            Text(footnote, style = MaterialTheme.typography.labelSmall, color = W.Ghost, maxLines = 1)
        }
    }
}

/**
 * A smoothed line with a gradient beneath it.
 *
 * Curves are drawn as cubics through the horizontal midpoint between each pair of points, which
 * smooths the series without letting it overshoot into values that were never recorded -- a
 * spline that invents a dip below zero is a lie about the data.
 */
@Composable
fun AreaChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = W.Gold,
    height: Dp = 150.dp,
    showGrid: Boolean = true,
    fillFromZero: Boolean = false,
) {
    if (values.size < 2) {
        ChartPlaceholder(
            modifier = modifier,
            height = height,
            message = if (values.isEmpty()) "No data yet" else "One reading — log another to see a trend",
        )
        return
    }

    val progress = animateValue(1f, durationMillis = 900)
    val minValue = if (fillFromZero) 0.0 else values.min()
    val maxValue = values.max()
    val span = (maxValue - minValue).takeIf { it > 0.00001 } ?: 1.0

    Canvas(modifier.fillMaxWidth().height(height)) {
        val padTop = 10f
        val padBottom = 8f
        val usable = size.height - padTop - padBottom
        val stepX = size.width / (values.size - 1)

        fun pointAt(i: Int): Offset {
            val ratio = ((values[i] - minValue) / span).toFloat().coerceIn(0f, 1f)
            return Offset(stepX * i, padTop + usable * (1f - ratio))
        }

        if (showGrid) {
            repeat(4) { row ->
                val y = padTop + usable * row / 3f
                drawLine(
                    color = W.Line.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
        }

        // How much of the series to reveal, so the line draws itself in on first appearance.
        val shownCount = max(2, (values.size * progress).toInt().coerceAtMost(values.size))

        val line = Path()
        line.moveTo(pointAt(0).x, pointAt(0).y)
        for (i in 1 until shownCount) {
            val prev = pointAt(i - 1)
            val cur = pointAt(i)
            val midX = (prev.x + cur.x) / 2f
            line.cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
        }

        val fill = Path()
        fill.addPath(line)
        val lastX = pointAt(shownCount - 1).x
        fill.lineTo(lastX, size.height)
        fill.lineTo(0f, size.height)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.10f), Color.Transparent),
            ),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.6f, cap = StrokeCap.Round),
        )

        // The head of the series: a solid dot inside a halo, so the newest value is unmistakable.
        val head = pointAt(shownCount - 1)
        drawCircle(color = color.copy(alpha = 0.22f), radius = 11f, center = head)
        drawCircle(color = color, radius = 4.2f, center = head)
        drawCircle(color = W.Void, radius = 1.8f, center = head)
    }
}

/**
 * Rounded columns.
 *
 * The final column is drawn in the accent colour: on every chart in this app the rightmost bar
 * is the current period, and it is the one being asked about.
 */
@Composable
fun BarChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = W.Gold,
    barColor: Color = W.NightTop,
    height: Dp = 132.dp,
) {
    if (values.isEmpty() || values.all { it <= 0.0 }) {
        ChartPlaceholder(modifier = modifier, height = height, message = "No volume logged in this window")
        return
    }
    val progress = animateValue(1f, durationMillis = 800)
    val maxValue = values.max().takeIf { it > 0.0 } ?: 1.0

    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = if (values.size > 20) 2f else 6f
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(2f)
        val radius = androidx.compose.ui.geometry.CornerRadius(min(barWidth / 2.4f, 7f))

        values.forEachIndexed { index, value ->
            val ratio = (value / maxValue).toFloat().coerceIn(0f, 1f) * progress
            // Every column keeps a visible foot even at zero, so the axis reads as a series
            // rather than as gaps of nothing.
            val h = max(3f, size.height * ratio)
            val x = index * (barWidth + gap)
            val isLatest = index == values.lastIndex
            drawRoundRect(
                brush = if (isLatest) {
                    Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)))
                } else {
                    Brush.verticalGradient(listOf(barColor, barColor.copy(alpha = 0.45f)))
                },
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }
    }
}

/** Volume share by muscle group. Slices are kilograms and sum to the whole, so a ring is honest. */
@Composable
fun DonutChart(
    slices: List<Pair<Color, Double>>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val total = slices.sumOf { it.second }
    val progress = animateValue(1f, durationMillis = 850)

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)

            drawArc(
                color = W.Line.copy(alpha = 0.5f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke),
            )
            if (total <= 0.0) return@Canvas

            var start = -90f
            slices.forEach { (color, value) ->
                val sweep = (value / total).toFloat() * 360f * progress
                if (sweep > 0.4f) {
                    drawArc(
                        color = color,
                        startAngle = start,
                        // A degree of air between slices so adjacent colours stay separable.
                        sweepAngle = (sweep - 1.2f).coerceAtLeast(0.6f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        content()
    }
}

/** A tiny inline trend, for a row that has no room for a real chart. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = W.Gold,
) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    val minValue = values.min()
    val span = (values.max() - minValue).takeIf { it > 0.00001 } ?: 1.0
    Canvas(modifier) {
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val y = size.height * (1f - ((v - minValue) / span).toFloat()).coerceIn(0f, 1f)
            val x = stepX * i
            if (i == 0) path.moveTo(x, y) else {
                val prevX = stepX * (i - 1)
                val prevY = size.height *
                    (1f - ((values[i - 1] - minValue) / span).toFloat()).coerceIn(0f, 1f)
                val midX = (prevX + x) / 2f
                path.cubicTo(midX, prevY, midX, y, x, y)
            }
        }
        drawPath(path, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}

@Composable
private fun ChartPlaceholder(modifier: Modifier, height: Dp, message: String) {
    Box(
        modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = W.Ghost)
    }
}

/** Axis captions under a chart. First, middle and last only -- more than that becomes noise. */
@Composable
fun ChartAxis(labels: List<String>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    val shown = when {
        labels.size <= 3 -> labels
        else -> listOf(labels.first(), labels[labels.size / 2], labels.last())
    }
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        shown.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = W.Ghost)
        }
    }
}

/** A legend entry: a colour swatch, a name, and its share. */
@Composable
fun LegendRow(
    color: Color,
    name: String,
    value: String,
    share: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = W.Ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = W.Muted)
        Spacer(Modifier.width(10.dp))
        Text(
            share,
            style = MaterialTheme.typography.labelSmall,
            color = W.Faint,
            modifier = Modifier.width(38.dp),
        )
    }
}

/** The palette slices are assigned from, ordered so neighbouring slices never sit close in hue. */
val ChartPalette: List<Color> = listOf(
    W.Gold,
    W.Cyan,
    W.Good,
    Color(0xFFD98F5F),
    Color(0xFF8E7BD4),
    Color(0xFF5FBFB0),
    Color(0xFFD4708A),
    Color(0xFFB0C05F),
    Color(0xFF7F94B8),
    Color(0xFFC9A227),
    Color(0xFF6FA8A0),
    Color(0xFF9C8AA8),
)
