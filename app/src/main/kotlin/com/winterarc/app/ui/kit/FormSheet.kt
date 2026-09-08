package com.winterarc.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W

/**
 * A full-screen form.
 *
 * Two things here are load-bearing and easy to get wrong.
 *
 * FIRST, THE HEIGHT IS SET EXPLICITLY. A dialog window wraps its content, so it hands the content
 * an unbounded height -- `fillMaxSize()` does nothing against an infinite constraint. A `weight`
 * child measured against infinity expands to its full content height, which means the scrolling
 * body never has anything left to scroll and every sibling below it is pushed off the screen.
 * That is what silently swallowed the save button. Binding the height to the screen restores
 * ordinary bounded layout: the body scrolls, and the action below it stays put.
 *
 * SECOND, THE PRIMARY ACTION APPEARS TWICE, ON PURPOSE. Once in the header, where nothing can push
 * it out of view, and once at the foot of the form where it is naturally reached. The header copy
 * is the guarantee: a form whose only submit button lives at the bottom is one layout bug away
 * from being unusable, and this one already was.
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
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(screenHeight)
                .background(Grad.screen),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding(),
            ) {
                // -- header, with the action that can never be clipped ------------------------
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

                // -- the form itself ----------------------------------------------------------
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
                    // Keeps the last field clear of the action bar on a form that fills the screen.
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
}

/** The compact toolbar copy of the primary action. */
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
