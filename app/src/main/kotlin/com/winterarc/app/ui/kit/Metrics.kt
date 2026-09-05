package com.winterarc.app.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.theme.W

/**
 * A row of stat cards that are all the same height.
 *
 * Laid out at [IntrinsicSize.Max] so every card is measured against the tallest, rather than each
 * sizing to its own text. Without this a card carrying a caption stands taller than one that does
 * not, and a row of three reads as ragged even though nothing is misaligned.
 */
@Composable
fun MetricRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 11.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/**
 * One card in a [MetricRow].
 *
 * The card fills the row's height and the caption is pushed to the bottom, so captions sit on a
 * common line across the row instead of floating at whatever height their own value left them.
 */
@Composable
fun RowScope.MetricTile(
    label: String,
    value: String,
    unit: String? = null,
    caption: String? = null,
    valueColor: Color = W.Ink,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (ColumnScope.() -> Unit)? = null,
) {
    ArcCard(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        onClick = onClick,
        padding = PaddingValues(15.dp),
    ) {
        Column(Modifier.fillMaxHeight()) {
            Label(label)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = valueColor,
                    maxLines = 1,
                )
                if (unit != null) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.titleSmall,
                        color = W.Faint,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.height(7.dp))
                trailing()
            }
            // Takes up whatever height the tallest card in the row imposed, so the caption below
            // it lands on the same line as its neighbours'.
            Spacer(Modifier.weight(1f))
            if (caption != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Faint,
                    maxLines = 2,
                )
            }
        }
    }
}
