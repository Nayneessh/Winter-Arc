package com.winterarc.app.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W

/**
 * A full-screen form, drawn in the app's own window.
 *
 * It is deliberately NOT a Dialog. A dialog gets its own window, and that window sizes itself to
 * its content -- so the content is measured against an unbounded height, `fillMaxSize()` has
 * nothing to fill, and a `weight` child has no remaining space to share. The scrolling body took
 * its whole content height, leaving nothing to scroll, and the action bar below it was laid out
 * past the bottom of the screen. Dialog windows also report window insets inconsistently across
 * manufacturers, so even correcting the height left the footer sitting underneath the navigation
 * bar on some devices.
 *
 * Drawn in the app's own window instead, every one of those problems disappears: the height is
 * bounded by the screen, `weight` behaves, and `windowInsetsPadding` reports the same real values
 * the bottom navigation bar already relies on.
 *
 * The primary action still appears twice -- in the header and at the foot. That redundancy stays:
 * this form shipped unusable twice because its only submit lived somewhere that could be pushed
 * off screen, and the header copy sits above the scrolling region where nothing can move it.
 */
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    subtitle: String? = null,
    primaryEnabled: Boolean = true,
    footer: @Composable (ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .zIndex(10f)
            .fillMaxSize()
            .background(Grad.screen)
            // Swallows taps so nothing behind the form can be pressed through it.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = W.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = W.Faint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                HeaderSave(enabled = primaryEnabled, onClick = onPrimary)
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.Filled.Close, "Close", onDismiss, size = 38.dp)
            }

            HairLine()

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 18.dp, bottom = 12.dp),
            ) {
                content()
                if (footer != null) {
                    Spacer(Modifier.height(24.dp))
                    footer()
                }
                Spacer(Modifier.height(20.dp))
            }

            HairLine()

            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                GoldButton(
                    primaryLabel,
                    onPrimary,
                    Modifier.fillMaxWidth(),
                    enabled = primaryEnabled,
                )
            }
        }
    }
}

/** The toolbar copy of the primary action, above the scrolling region and immune to it. */
@Composable
private fun HeaderSave(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (enabled) W.GoldFilm else W.Void.copy(alpha = 0.4f))
            .border(1.dp, if (enabled) W.GoldEdge else W.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "SAVE",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) W.GoldBright else W.Faint,
        )
    }
}
