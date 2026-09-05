package com.winterarc.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Fmt
import com.winterarc.core.SetDirection
import com.winterarc.core.SetPoint
import com.winterarc.core.WeightUnit

/** The colour a set is drawn in: what it did relative to the set before it. */
fun directionColor(direction: SetDirection, isTop: Boolean, warmup: Boolean): Color = when {
    warmup -> W.Ghost
    isTop -> W.Gold
    direction == SetDirection.UP -> W.Good
    direction == SetDirection.DOWN -> W.Warn
    direction == SetDirection.HELD -> W.Cyan
    else -> W.Muted
}

private fun directionGlyph(direction: SetDirection): String = when (direction) {
    SetDirection.UP -> "▲"
    SetDirection.DOWN -> "▼"
    SetDirection.HELD -> "="
    SetDirection.FIRST -> ""
}

/**
 * One column per set, height proportional to the load.
 *
 * Colour carries the movement between sets -- green climbed, amber dropped, blue held the same
 * load for the same reps, gold is the top set of the movement. That makes the shape of a
 * movement legible at a glance: a clean ramp, a plateau, or a fade at the end.
 *
 * Bars are laid out bottom-aligned so the labels beneath them line up across the row regardless
 * of how tall each bar is.
 */
@Composable
fun SetLadder(
    sets: List<SetPoint>,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 96.dp,
) {
    if (sets.isEmpty()) return
    val heaviest = sets.maxOf { it.weightKg }.takeIf { it > 0.0 } ?: 1.0

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        sets.forEach { point ->
            val color = directionColor(point.direction, point.isTop, point.warmup)
            // Every bar keeps a visible foot so a light set still reads as a set.
            val height = 16.dp + barHeight * (point.weightKg / heaviest).toFloat()

            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                val glyph = directionGlyph(point.direction)
                Text(
                    glyph.ifBlank { " " },
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    Fmt.weight(point.weightKg, unit),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (point.isTop) W.GoldBright else W.Ink,
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.30f)),
                            ),
                        )
                        .border(
                            width = if (point.isTop) 1.dp else 0.dp,
                            color = if (point.isTop) W.GoldBright else Color.Transparent,
                            shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp),
                        ),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "×${point.reps}",
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    if (point.warmup) "W" else point.index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Explains the colours once, so no chart has to be decoded twice. */
@Composable
fun SetLadderLegend(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(W.Gold, "top set")
        LegendDot(W.Good, "up")
        LegendDot(W.Warn, "down")
        LegendDot(W.Cyan, "held")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = W.Ghost)
    }
}

/** A signed change against the previous performance of the same movement. */
@Composable
fun ChangePill(deltaKg: Double?, unit: WeightUnit, modifier: Modifier = Modifier) {
    if (deltaKg == null) {
        Pill("first time", modifier = modifier, color = W.Ghost)
        return
    }
    val display = Fmt.toDisplayWeight(deltaKg, unit)
    val color = when {
        display > 0.05 -> W.Good
        display < -0.05 -> W.Warn
        else -> W.Faint
    }
    val text = when {
        display > 0.05 -> "▲ ${Fmt.signed(display, 1).removePrefix("+")} ${unit.suffix}"
        display < -0.05 -> "▼ ${Fmt.signed(-display, 1).removePrefix("+")} ${unit.suffix}"
        else -> "same as last"
    }
    Pill(text, modifier = modifier, color = color, background = color.copy(alpha = 0.13f))
}
