package com.winterarc.app.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W

/** The app's ground: one continuous gradient, so screens feel like one body, not a stack of pages. */
@Composable
fun WinterBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Grad.screen)) { content() }
}

/**
 * The standard raised surface.
 *
 * Two details do the work. The fill is a vertical gradient rather than a flat colour, and a
 * one-pixel light gradient is laid along the top edge. Together they read as a panel catching
 * light from above -- which is the difference between a designed surface and a coloured
 * rectangle.
 */
@Composable
fun ArcCard(
    modifier: Modifier = Modifier,
    brush: Brush = Grad.card,
    borderColor: Color = W.Line,
    radius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                    ),
                ),
        )
        Column(Modifier.padding(padding), content = content)
    }
}

/** The small wide-tracked caption that names every figure in the app. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = W.Faint,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A short gold rule anchors the heading without spending gold on the text itself.
            Box(
                Modifier
                    .size(width = 3.dp, height = 13.dp)
                    .clip(CircleShape)
                    .background(W.Gold),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = W.Muted,
            )
        }
        trailing?.invoke()
    }
}

/**
 * A single figure with its label.
 *
 * [value] is deliberately allowed to be large and is drawn in the display scale; the unit rides
 * beside it at a fraction of the size so the number itself is what the eye lands on.
 */
@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    valueColor: Color = W.Ink,
    big: Boolean = false,
) {
    Column(modifier) {
        Label(label)
        Spacer(Modifier.height(if (big) 8.dp else 5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = if (big) MaterialTheme.typography.displaySmall
                else MaterialTheme.typography.headlineMedium,
                color = valueColor,
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleSmall,
                    color = W.Faint,
                    modifier = Modifier.padding(bottom = if (big) 6.dp else 3.dp),
                )
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = W.Faint)
        }
    }
}

/** A metric already wrapped in its own card -- the unit the dashboard grid is built from. */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    valueColor: Color = W.Ink,
    onClick: (() -> Unit)? = null,
) {
    ArcCard(modifier = modifier, onClick = onClick, padding = PaddingValues(16.dp)) {
        Metric(label = label, value = value, unit = unit, caption = caption, valueColor = valueColor)
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = W.Muted,
    background: Color = W.Void.copy(alpha = 0.5f),
    borderColor: Color = Color.Transparent,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** A hairline. Kept faint: structure should be felt rather than drawn. */
@Composable
fun HairLine(modifier: Modifier = Modifier, color: Color = W.LineSoft) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A hollow ring: the empty form of the app's own arc mark.
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(2.dp, W.Ghost, CircleShape),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = W.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = W.Faint,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * Runs a value up from zero on first appearance, and eases between values afterwards.
 *
 * Applied to every headline figure and every chart. Numbers that count into place make the
 * screen feel alive on open, which is most of what separates a dashboard that feels built from
 * one that feels generated.
 */
@Composable
fun animateValue(target: Float, durationMillis: Int = 750, delayMillis: Int = 0): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        anim.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        )
    }
    return anim.value
}
