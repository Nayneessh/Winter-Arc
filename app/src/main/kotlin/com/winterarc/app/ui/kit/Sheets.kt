package com.winterarc.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.theme.W

/**
 * The app's bottom sheet.
 *
 * Wrapped in one place so that the drag handle, colours and corner radius are decided once.
 * Sheets are used for anything that modifies the thing already on screen -- swapping a movement,
 * editing a target -- because they keep that context visible behind them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinterSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = W.NightHi,
        contentColor = W.Ink,
        scrimColor = Color(0xCC02090A),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(W.Ghost),
                )
            }
        },
    ) {
        Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp), content = content)
    }
}

/** A tappable row inside a sheet: an icon, a name, and an optional explanation. */
@Composable
fun SheetAction(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = W.Ink,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = W.Faint)
            }
        }
    }
}

@Composable
fun SheetTitle(text: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = W.Ink)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = W.Faint)
        }
    }
}
