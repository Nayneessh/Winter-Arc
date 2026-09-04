package com.winterarc.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W

/**
 * The frame for anything opened on top of the tabs.
 *
 * It repaints the gradient ground so an overlay is fully opaque over whatever it covers, and it
 * puts the back affordance in exactly the same place every time.
 */
@Composable
fun OverlayScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Grad.screen)) {
        Column(modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = W.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = W.Faint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}
