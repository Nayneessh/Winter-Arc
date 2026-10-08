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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
    onPick: (String, LocalDate) -> Unit,
    onOpenSession: (LocalDate) -> Unit,
) {
    val routines = data.activeProgramme?.routines.orEmpty().filterNot { it.archived }
    var date by remember { mutableStateOf(LocalDate.now()) }

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

            // Sessions can be dated into the past so training that was never logged at the time --
            // or was lost -- can still be entered against the day it actually happened.
            DateRow(date = date, onChange = { date = it })
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
                            .clickable { onPick(routine.id, date) }
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
            GhostButton("Empty session", { onOpenSession(date) }, Modifier.fillMaxWidth())
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

private val pickerDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

/**
 * Chooses the day a session is recorded against.
 *
 * Stepped rather than a calendar: the dates that matter here are almost always within the last
 * week or two, and a tap per day beats opening a month view. It will not go past today, because a
 * session in the future is never something that happened.
 */
@Composable
private fun DateRow(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val daysBack = ChronoUnit.DAYS.between(date, today).toInt()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(W.Void.copy(alpha = 0.45f))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayStep("−") { onChange(date.minusDays(1)) }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date.format(pickerDate),
                style = MaterialTheme.typography.titleSmall,
                color = W.Ink,
            )
            Text(
                when (daysBack) {
                    0 -> "Today"
                    1 -> "Yesterday"
                    else -> "$daysBack days ago"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (daysBack == 0) W.Ghost else W.Gold,
            )
        }
        DayStep("+", enabled = date.isBefore(today)) { onChange(date.plusDays(1)) }
    }
}

@Composable
private fun DayStep(symbol: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(W.NightHi)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.headlineSmall,
            color = if (enabled) W.Gold else W.Ghost,
        )
    }
}
