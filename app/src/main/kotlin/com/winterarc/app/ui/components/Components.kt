package com.winterarc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.theme.WinterArcColors

/** A raised night-green panel — the standard container for everything on a screen. */
@Composable
fun WinterCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (accent) WinterArcColors.NightElevated else WinterArcColors.NightSurface)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(16.dp),
        content = content,
    )
}

/** Section heading in muted uppercase — used to break long screens into scannable blocks. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = WinterArcColors.Muted,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/**
 * A single headline figure with its label. Gold by default because these are the numbers
 * the user is looking for; pass a colour explicitly where the value is contextual.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    valueColor: Color = WinterArcColors.GoldBright,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = WinterArcColors.Muted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = WinterArcColors.Faint,
            )
        }
    }
}

/**
 * The primary call to action. Deliberately tall (60dp) — this is pressed with sweaty hands
 * between sets, and Material's default 40dp target is too small for that.
 */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WinterArcColors.Gold,
            contentColor = WinterArcColors.NightDeep,
            disabledContainerColor = WinterArcColors.GoldDim.copy(alpha = 0.35f),
            disabledContentColor = WinterArcColors.Faint,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** Secondary action — outlined so it never competes with the gold primary. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WinterArcColors.NightBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = WinterArcColors.White,
            disabledContentColor = WinterArcColors.Faint,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Large tap-to-adjust numeric control.
 *
 * Typing a decimal weight on a phone mid-set is slow and error-prone, so the primary
 * interaction is +/- at a sensible increment; the value itself stays tappable for the cases
 * where a big jump is faster to type.
 */
@Composable
fun NumberStepper(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = WinterArcColors.Muted,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("−", onDecrement)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onValueClick)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = WinterArcColors.GoldBright,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (suffix != null) {
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.labelSmall,
                        color = WinterArcColors.Faint,
                    )
                }
            }
            StepperButton("+", onIncrement)
        }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WinterArcColors.NightElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.headlineSmall,
            color = WinterArcColors.Gold,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Small pill used for muscle groups, styles and superset markers. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WinterArcColors.Muted,
    background: Color = WinterArcColors.NightElevated,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Consistent empty state — never a blank screen, always says what to do next. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WinterArcColors.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = WinterArcColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}
