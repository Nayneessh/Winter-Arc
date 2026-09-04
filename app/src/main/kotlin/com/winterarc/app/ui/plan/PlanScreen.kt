package com.winterarc.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
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
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.OverlayScreen
import com.winterarc.app.ui.kit.Pill
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Actions
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import com.winterarc.core.WeekDays
import com.winterarc.core.Routine
import com.winterarc.core.Seed
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

private val programmeDate = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The programme, laid out as the week it actually is.
 *
 * A weekday with no routine is shown as rest rather than omitted: the shape of the week is the
 * information, and a missing row would read as an oversight instead of a decision.
 */
@Composable
fun PlanScreen(
    data: AppData,
    onUpdate: ((AppData) -> AppData) -> Unit,
    onBack: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val programme = data.activeProgramme
    var creating by remember { mutableStateOf(false) }

    OverlayScreen(
        title = programme?.name ?: "Programme",
        subtitle = programme?.let {
            "${it.startDate.format(programmeDate)} — ${it.endDate.format(programmeDate)}"
        },
        onBack = onBack,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 34.dp),
        ) {
            item {
                SectionHeader("The week")
            }

            items(WeekDays.size) { index ->
                val day = WeekDays[index]
                val routine = programme?.routines?.firstOrNull { !it.archived && day in it.days }
                DayRow(
                    day = day,
                    routine = routine,
                    onClick = { routine?.let { onOpenRoutine(it.id) } },
                )
            }

            item {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    GhostButton(
                        "New routine",
                        { creating = true },
                        Modifier.weight(1f),
                        icon = Icons.Filled.Add,
                    )
                    GhostButton(
                        "Movements",
                        onOpenLibrary,
                        Modifier.weight(1f),
                        icon = Icons.Filled.FitnessCenter,
                    )
                }
                Spacer(Modifier.height(26.dp))
            }

            item {
                SectionHeader("How this programme is run")
                ArcCard {
                    Seed.principles.forEachIndexed { index, (title, body) ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        Row {
                            Box(
                                Modifier
                                    .padding(top = 5.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(W.Gold),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = W.Ink,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = W.Faint,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating && programme != null) {
        NewRoutineSheet(
            onDismiss = { creating = false },
            onCreate = { routine ->
                onUpdate { Actions.addRoutine(it, programme.id, routine) }
                creating = false
                onOpenRoutine(routine.id)
            },
        )
    }
}

@Composable
private fun DayRow(day: DayOfWeek, routine: Routine?, onClick: () -> Unit) {
    val accent = routine?.let { W.accent(it.accent) } ?: W.Ghost

    ArcCard(
        Modifier.padding(bottom = 9.dp),
        onClick = if (routine != null) onClick else null,
        padding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.shortDay(day).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (routine != null) W.Muted else W.Ghost,
                modifier = Modifier.width(38.dp),
            )
            Box(Modifier.size(width = 2.dp, height = 32.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(13.dp))

            if (routine == null) {
                Column(Modifier.weight(1f)) {
                    Text("Rest", style = MaterialTheme.typography.titleMedium, color = W.Faint)
                    Text(
                        "Nothing scheduled",
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Ghost,
                    )
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium, color = W.Ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${routine.items.size} movements · ${routine.totalSets} sets · " +
                            Fmt.duration(routine.estimatedMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Faint,
                    )
                }
                if (routine.items.any { it.priority != com.winterarc.core.Priority.NONE }) {
                    Pill(
                        routine.items.first { it.priority != com.winterarc.core.Priority.NONE }
                            .priority.symbol,
                        color = W.Gold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewRoutineSheet(onDismiss: () -> Unit, onCreate: (Routine) -> Unit) {
    var name by remember { mutableStateOf("") }
    val selectedDays = remember { mutableStateOf(setOf<DayOfWeek>()) }

    com.winterarc.app.ui.kit.WinterSheet(onDismiss = onDismiss) {
        com.winterarc.app.ui.kit.SheetTitle(
            "New routine",
            "Give it a name and the days it runs on. Movements come next.",
        )
        com.winterarc.app.ui.kit.WinterField(
            value = name,
            onValueChange = { name = it },
            label = "Routine name",
        )
        Spacer(Modifier.height(16.dp))
        Label("Days")
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekDays.forEach { day ->
                val on = day in selectedDays.value
                Text(
                    Fmt.shortDay(day).take(1),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (on) W.Void else W.Muted,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (on) W.Gold else W.Void.copy(alpha = 0.5f))
                        .clickable {
                            selectedDays.value = if (on) {
                                selectedDays.value - day
                            } else {
                                selectedDays.value + day
                            }
                        }
                        .padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        com.winterarc.app.ui.kit.GoldButton(
            "CREATE",
            {
                if (name.isNotBlank()) {
                    onCreate(
                        Routine(
                            name = name.trim(),
                            days = selectedDays.value.toList().sortedBy { it.value },
                        ),
                    )
                }
            },
            Modifier.fillMaxWidth(),
        )
    }
}
