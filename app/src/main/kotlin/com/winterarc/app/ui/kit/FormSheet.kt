package com.winterarc.app.ui.kit

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W

/**
 * A full-screen form.
 *
 * Anything with more than two or three fields belongs here rather than in a bottom sheet. A sheet
 * holding a long form has to scroll inside a container that is itself scrollable and sized to its
 * content: the two fight, the bottom of the form is clipped against the edge of the screen, and
 * the save button becomes unreachable -- which makes the form not merely awkward but unusable.
 *
 * Here the layout is explicit and cannot collapse: a fixed header, a body that takes the
 * remaining height and scrolls on its own, and an action pinned to the bottom that is always
 * visible. [imePadding] lifts the whole thing clear of the keyboard.
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Grad.screen)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    RoundIcon(Icons.Filled.Close, "Close", onDismiss)
                }

                HairLine()

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 18.dp, bottom = 24.dp),
                    content = content,
                )

                HairLine()

                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    GoldButton(
                        primaryLabel,
                        onPrimary,
                        Modifier.fillMaxWidth(),
                        enabled = primaryEnabled,
                    )
                    footer?.invoke(this)
                }
            }
        }
    }
}
