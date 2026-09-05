package com.winterarc.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.EmptyState
import com.winterarc.app.ui.kit.FormSheet
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.GoldButton
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.OverlayScreen
import com.winterarc.app.ui.kit.Pill
import com.winterarc.app.ui.kit.RoundIcon
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.kit.SheetTitle
import com.winterarc.app.ui.kit.WinterField
import com.winterarc.app.ui.kit.WinterSheet
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Accent
import com.winterarc.core.Actions
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import com.winterarc.core.WeekDays
import com.winterarc.core.PlanItem
import com.winterarc.core.Priority
import java.time.DayOfWeek

/**
 * Editing a routine.
 *
 * Changes here affect future sessions only. Anything already performed keeps the prescription it
 * was performed under, which is what makes history worth keeping at all.
 */
@Composable
fun RoutineEditorScreen(
    data: AppData,
    routineId: String,
    onUpdate: ((AppData) -> AppData) -> Unit,
    onBack: () -> Unit,
) {
    val programme = data.activeProgramme
    val routine = programme?.routines?.firstOrNull { it.id == routineId }

    if (programme == null || routine == null) {
        OverlayScreen(title = "Routine", onBack = onBack) {
            EmptyState("Not found", "This routine is no longer part of your programme.")
        }
        return
    }

    var adding by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<String?>(null) }
    var editingDetails by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    OverlayScreen(
        title = routine.name,
        subtitle = routine.days.joinToString(" · ") { Fmt.dayLabel(it) }.ifBlank { "Not scheduled" },
        onBack = onBack,
        actions = {
            RoundIcon(Icons.Filled.DeleteOutline, "Delete routine", { confirmDelete = true }, tint = W.Bad)
        },
    ) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 34.dp),
        ) {
            item {
                ArcCard(onClick = { editingDetails = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label("Routine")
                            Spacer(Modifier.height(6.dp))
                            Text(routine.name, style = MaterialTheme.typography.titleLarge, color = W.Ink)
                            if (routine.subtitle.isNotBlank()) {
                                Text(
                                    routine.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = W.Faint,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${routine.items.size} movements · ${routine.totalSets} sets · " +
                                    Fmt.duration(routine.estimatedMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = W.Ghost,
                            )
                        }
                        Box(
                            Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(W.accent(routine.accent)),
                        )
                    }
                    if (routine.note.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(routine.note, style = MaterialTheme.typography.bodySmall, color = W.Warn)
                    }
                }
                Spacer(Modifier.height(20.dp))
                SectionHeader("Movements")
            }

            items(routine.items.size) { index ->
                val item = routine.items[index]
                val sharesWithNext = routine.items.getOrNull(index + 1)?.groupLetter == item.groupLetter

                Column {
                    ArcCard(
                        Modifier.padding(bottom = if (sharesWithNext) 2.dp else 9.dp),
                        onClick = { editingItem = item.id },
                        padding = PaddingValues(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(W.Void.copy(alpha = 0.55f))
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    item.group,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.priority == Priority.NONE) W.Faint else W.Gold,
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    data.exerciseName(item.exerciseId),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = W.Ink,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${item.sets} × ${item.repRange}" +
                                        (if (item.rir.isNotBlank()) " · RIR ${item.rir}" else "") +
                                        " · ${Fmt.clock(item.restSeconds)} rest",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = W.Faint,
                                )
                            }
                            Column {
                                Icon(
                                    Icons.Filled.ArrowUpward, "Move up",
                                    tint = if (index == 0) W.Ghost else W.Faint,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = index > 0) {
                                            onUpdate {
                                                Actions.movePlanItem(it, programme.id, routineId, index, index - 1)
                                            }
                                        }
                                        .padding(5.dp),
                                )
                                Icon(
                                    Icons.Filled.ArrowDownward, "Move down",
                                    tint = if (index == routine.items.lastIndex) W.Ghost else W.Faint,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = index < routine.items.lastIndex) {
                                            onUpdate {
                                                Actions.movePlanItem(it, programme.id, routineId, index, index + 1)
                                            }
                                        }
                                        .padding(5.dp),
                                )
                            }
                        }
                        if (item.cue.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(item.cue, style = MaterialTheme.typography.labelSmall, color = W.Ghost)
                        }
                    }
                    if (sharesWithNext) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 26.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(2.dp).height(13.dp).background(W.GoldEdge))
                            Spacer(Modifier.width(9.dp))
                            Text(
                                "SUPERSET",
                                style = MaterialTheme.typography.labelSmall,
                                color = W.Ghost,
                            )
                        }
                    }
                }
            }

            item {
                if (routine.items.isEmpty()) {
                    EmptyState(
                        title = "No movements yet",
                        message = "Add the exercises this routine prescribes. Sets, reps and rest " +
                            "are set per movement.",
                    )
                }
                Spacer(Modifier.height(10.dp))
                GhostButton(
                    "Add a movement",
                    { adding = true },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Add,
                )
            }
        }
    }

    if (adding) {
        ExercisePickerSheet(
            data = data,
            title = "Add to ${routine.name}",
            onDismiss = { adding = false },
            onPick = { exerciseId ->
                onUpdate {
                    Actions.addPlanItem(
                        it, programme.id, routineId,
                        PlanItem(exerciseId = exerciseId, group = "", sets = 3, repLow = 8, repHigh = 12),
                    )
                }
                adding = false
            },
        )
    }

    editingItem?.let { itemId ->
        val item = routine.items.firstOrNull { it.id == itemId }
        if (item == null) {
            editingItem = null
        } else {
            PlanItemSheet(
                item = item,
                name = data.exerciseName(item.exerciseId),
                onDismiss = { editingItem = null },
                onSave = { updated ->
                    onUpdate { Actions.updatePlanItem(it, programme.id, routineId, updated) }
                    editingItem = null
                },
                onDelete = {
                    onUpdate { Actions.removePlanItem(it, programme.id, routineId, itemId) }
                    editingItem = null
                },
            )
        }
    }

    if (editingDetails) {
        RoutineDetailsSheet(
            name = routine.name,
            subtitle = routine.subtitle,
            note = routine.note,
            minutes = routine.estimatedMinutes,
            days = routine.days.toSet(),
            accent = routine.accent,
            onDismiss = { editingDetails = false },
            onSave = { name, subtitle, note, minutes, days, accent ->
                onUpdate {
                    Actions.updateRoutine(
                        it, programme.id,
                        routine.copy(
                            name = name,
                            subtitle = subtitle,
                            note = note,
                            estimatedMinutes = minutes,
                            days = days.toList().sortedBy { d -> d.value },
                            accent = accent,
                        ),
                    )
                }
                editingDetails = false
            },
        )
    }

    if (confirmDelete) {
        WinterSheet(onDismiss = { confirmDelete = false }) {
            SheetTitle(
                "Delete ${routine.name}?",
                "Sessions already performed from this routine stay in your history untouched.",
            )
            GoldButton(
                "Delete routine",
                {
                    onUpdate { Actions.deleteRoutine(it, programme.id, routineId) }
                    confirmDelete = false
                    onBack()
                },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(9.dp))
            GhostButton("Keep it", { confirmDelete = false }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlanItemSheet(
    item: PlanItem,
    name: String,
    onDismiss: () -> Unit,
    onSave: (PlanItem) -> Unit,
    onDelete: () -> Unit,
) {
    var sets by remember { mutableIntStateOf(item.sets) }
    var low by remember { mutableIntStateOf(item.repLow) }
    var high by remember { mutableIntStateOf(item.repHigh) }
    var rest by remember { mutableIntStateOf(item.restSeconds) }
    var group by remember { mutableStateOf(item.group) }
    var rir by remember { mutableStateOf(item.rir) }
    var cue by remember { mutableStateOf(item.cue) }

    FormSheet(
        title = name,
        subtitle = "How this movement is prescribed",
        onDismiss = onDismiss,
        primaryLabel = "SAVE",
        onPrimary = {
            onSave(
                item.copy(
                    sets = sets, repLow = low, repHigh = high, restSeconds = rest,
                    group = group.trim().ifBlank { item.group },
                    rir = rir.trim(), cue = cue.trim(),
                )
            )
        },
        footer = {
            Spacer(Modifier.height(10.dp))
            GhostButton(
                "Remove from routine",
                onDelete,
                Modifier.fillMaxWidth(),
                icon = Icons.Filled.DeleteOutline,
                color = W.Bad,
            )
        },
    ) {
        NumberRow("Sets", sets.toString()) { sets = (sets + it).coerceIn(1, 30) }
        NumberRow("Lowest reps", low.toString()) { low = (low + it).coerceIn(1, 100) }
        NumberRow("Highest reps", high.toString()) { high = (high + it).coerceIn(low, 100) }
        NumberRow("Rest", Fmt.clock(rest), step = 15) { rest = (rest + it).coerceIn(0, 900) }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            WinterField(group, { group = it }, "Group (A1, B2…)", Modifier.weight(1f))
            WinterField(rir, { rir = it }, "RIR", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        WinterField(cue, { cue = it }, "Cue", singleLine = false)

        Spacer(Modifier.height(14.dp))
        Text(
            "Movements sharing a group letter are treated as a superset.",
            style = MaterialTheme.typography.labelSmall,
            color = W.Ghost,
        )
    }
}

@Composable
private fun RoutineDetailsSheet(
    name: String,
    subtitle: String,
    note: String,
    minutes: Int,
    days: Set<DayOfWeek>,
    accent: Accent,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Set<DayOfWeek>, Accent) -> Unit,
) {
    var nameState by remember { mutableStateOf(name) }
    var subtitleState by remember { mutableStateOf(subtitle) }
    var noteState by remember { mutableStateOf(note) }
    var minutesState by remember { mutableIntStateOf(minutes) }
    var daysState by remember { mutableStateOf(days) }
    var accentState by remember { mutableStateOf(accent) }

    FormSheet(
        title = "Routine details",
        onDismiss = onDismiss,
        primaryLabel = "SAVE",
        primaryEnabled = nameState.isNotBlank(),
        onPrimary = {
            onSave(
                nameState.trim().ifBlank { name },
                subtitleState.trim(),
                noteState.trim(),
                minutesState,
                daysState,
                accentState,
            )
        },
    ) {
        WinterField(nameState, { nameState = it }, "Name")
        Spacer(Modifier.height(14.dp))
        WinterField(subtitleState, { subtitleState = it }, "Subtitle")
        Spacer(Modifier.height(14.dp))
        WinterField(noteState, { noteState = it }, "Note shown before starting", singleLine = false)

        Spacer(Modifier.height(20.dp))
        NumberRow("Estimated minutes", minutesState.toString(), step = 5) {
            minutesState = (minutesState + it).coerceIn(5, 240)
        }

        Spacer(Modifier.height(20.dp))
        Label("Days")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekDays.forEach { day ->
                val on = day in daysState
                Text(
                    Fmt.shortDay(day).take(1),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (on) W.Void else W.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (on) W.Gold else W.Void.copy(alpha = 0.5f))
                        .clickable { daysState = if (on) daysState - day else daysState + day }
                        .padding(vertical = 12.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Label("Colour")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Accent.entries.forEach { option ->
                val on = option == accentState
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(W.Void.copy(alpha = 0.5f))
                        .border(
                            1.dp,
                            if (on) W.accent(option) else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { accentState = option }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(if (on) 15.dp else 11.dp)
                            .clip(CircleShape)
                            .background(W.accent(option)),
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String, step: Int = 1, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = W.Ink, modifier = Modifier.weight(1f))
        StepBox("−") { onChange(-step) }
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = W.GoldBright,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(84.dp),
        )
        StepBox("+") { onChange(step) }
    }
}

@Composable
private fun StepBox(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(W.Void.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineSmall, color = W.Gold)
    }
}
