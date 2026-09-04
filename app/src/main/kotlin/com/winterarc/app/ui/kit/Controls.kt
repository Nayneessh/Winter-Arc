package com.winterarc.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W

/**
 * The primary action.
 *
 * 58dp tall rather than Material's 40. It is pressed between sets, with tired hands, often
 * without looking straight at it.
 */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(if (enabled) Grad.goldFill else Brush.horizontalGradient(listOf(W.Ghost, W.Ghost)))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.55f),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = W.Void, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = W.Void,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Secondary action: outlined so it can never compete with the gold primary. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = W.Ink,
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(W.Void.copy(alpha = 0.35f))
            .border(1.dp, W.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

/** A small round icon action, used in headers and on rows. */
@Composable
fun RoundIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = W.Muted,
    background: Color = W.NightHi,
    size: androidx.compose.ui.unit.Dp = 42.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, W.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}

/**
 * A segmented switch for mutually exclusive filters.
 *
 * The selection is a filled gold pill rather than an underline: at a glance, on a dark screen,
 * fill reads instantly and a 2dp rule does not.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(W.Void.copy(alpha = 0.55f))
            .border(1.dp, W.Line, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) W.GoldFilm else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) W.GoldEdge else Color.Transparent,
                        RoundedCornerShape(11.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) W.GoldBright else W.Faint,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun WinterField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.titleSmall) },
        placeholder = placeholder?.let { { Text(it, color = W.Ghost) } },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = W.Ink,
            unfocusedTextColor = W.Ink,
            focusedBorderColor = W.GoldEdge,
            unfocusedBorderColor = W.Line,
            focusedLabelColor = W.Gold,
            unfocusedLabelColor = W.Faint,
            cursorColor = W.Gold,
            focusedContainerColor = W.Void.copy(alpha = 0.4f),
            unfocusedContainerColor = W.Void.copy(alpha = 0.4f),
        ),
    )
}

@Composable
fun WinterSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = W.Void,
            checkedTrackColor = W.Gold,
            checkedBorderColor = W.Gold,
            uncheckedThumbColor = W.Faint,
            uncheckedTrackColor = W.Void,
            uncheckedBorderColor = W.Line,
        ),
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = W.Ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = W.Faint)
            }
        }
        trailing?.invoke()
    }
}

/**
 * A full-screen numeric keypad.
 *
 * The system keyboard is the wrong tool here: it is small, it covers the set being edited, and
 * it takes three taps to reach a decimal point. This is one screen of large targets, with the
 * increments this app actually uses offered as single taps.
 */
@Composable
fun NumberPadDialog(
    title: String,
    initial: String,
    unit: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    allowDecimal: Boolean = true,
    quickSteps: List<Double> = listOf(-2.5, -1.0, 1.0, 2.5),
) {
    var text by remember { mutableStateOf(initial) }

    fun append(ch: String) {
        text = when {
            ch == "." && (!allowDecimal || text.contains(".")) -> text
            text == "0" && ch != "." -> ch
            text.length >= 7 -> text
            else -> text + ch
        }
    }

    fun step(by: Double) {
        val current = text.toDoubleOrNull() ?: 0.0
        val next = (current + by).coerceAtLeast(0.0)
        text = if (allowDecimal) {
            if (next == next.toLong().toDouble()) next.toLong().toString() else next.toString()
        } else {
            next.toLong().toString()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Grad.cardLit)
                .border(1.dp, W.Line, RoundedCornerShape(26.dp))
                .padding(20.dp),
        ) {
            Label(title)
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text.ifBlank { "0" },
                    style = MaterialTheme.typography.displaySmall,
                    color = W.GoldBright,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = W.Faint,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                quickSteps.forEach { by ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(W.Void.copy(alpha = 0.5f))
                            .clickable { step(by) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (by > 0) "+${trimStep(by)}" else trimStep(by),
                            style = MaterialTheme.typography.titleSmall,
                            color = W.Gold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(if (allowDecimal) "." else "", "0", "<"),
            )
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (key.isBlank()) Color.Transparent else W.Void.copy(alpha = 0.5f))
                                .clickable(enabled = key.isNotBlank()) {
                                    if (key == "<") text = text.dropLast(1) else append(key)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (key == "<") {
                                Icon(
                                    Icons.Filled.Backspace, "Delete",
                                    tint = W.Muted, modifier = Modifier.size(21.dp),
                                )
                            } else if (key.isNotBlank()) {
                                Text(key, style = MaterialTheme.typography.headlineSmall, color = W.Ink)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Cancel", onDismiss, Modifier.weight(1f))
                GoldButton("Save", { onConfirm(text.ifBlank { "0" }) }, Modifier.weight(1f))
            }
        }
    }
}

private fun trimStep(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
