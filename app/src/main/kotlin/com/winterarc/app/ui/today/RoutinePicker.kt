package com.winterarc.app.ui.today

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W
import com.winterarc.core.AppData
import com.winterarc.core.Fmt

/**
 * Choosing a different session.
 *
 * Deviating from the plan is normal -- a gym is busy, a day is missed, something else is wanted
 * -- so it costs one tap and is never treated as an error state.
 */
@Composable
fun RoutinePickerDialog(
    data: AppData,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onOpenSession: () -> Unit,
) {
    val routines = data.activeProgramme?.routines.orEmpty().filterNot { it.archived }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Grad.cardLit)
                .border(1.dp, W.Line, RoundedCornerShape(26.dp))
                .padding(20.dp),
        ) {
            Label("Train something else")
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(routines, key = { it.id }) { routine ->
                    val accent = W.accent(routine.accent)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(W.Void.copy(alpha = 0.45f))
                            .clickable { onPick(routine.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium, color = W.Ink)
                            Text(
                                "${routine.items.size} movements · ${routine.totalSets} sets · " +
                                    Fmt.duration(routine.estimatedMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = W.Faint,
                            )
                        }
                        Text(
                            routine.days.joinToString(" ") { Fmt.shortDay(it) },
                            style = MaterialTheme.typography.labelSmall,
                            color = W.Ghost,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            GhostButton("Empty session", onOpenSession, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "An empty session starts with nothing prescribed — add whatever you actually do.",
                style = MaterialTheme.typography.labelSmall,
                color = W.Ghost,
            )
            Spacer(Modifier.height(12.dp))
            GhostButton("Cancel", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
