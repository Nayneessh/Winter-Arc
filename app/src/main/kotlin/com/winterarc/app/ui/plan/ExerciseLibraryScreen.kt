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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
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
import com.winterarc.core.Actions
import com.winterarc.core.AppData
import com.winterarc.core.Equipment
import com.winterarc.core.Exercise
import com.winterarc.core.Muscle
import com.winterarc.core.newId

/**
 * The movement catalogue.
 *
 * Anything can be added here, which is what lets the app follow a completely different programme
 * later without being rebuilt. Nothing is ever hard-deleted -- history points at these by id.
 */
@Composable
fun ExerciseLibraryScreen(
    data: AppData,
    onUpdate: ((AppData) -> AppData) -> Unit,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val filtered = remember(query, muscle, showArchived, data.exercises) {
        data.exercises
            .filter { showArchived || !it.archived }
            .filter { muscle == null || it.muscle == muscle }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.detail.contains(query, ignoreCase = true)
            }
            .sortedBy { it.name }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.muscle } }

    OverlayScreen(
        title = "Movements",
        subtitle = "${data.exercises.count { !it.archived }} available · " +
            "${data.exercises.count { it.custom }} added by you",
        onBack = onBack,
        actions = {
            RoundIcon(Icons.Filled.Add, "New movement", { creating = true }, tint = W.Gold)
        },
    ) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 34.dp),
        ) {
            item {
                WinterField(query, { query = it }, "Search movements")
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item { Chip("All", muscle == null) { muscle = null } }
                    items(Muscle.entries.toList(), key = { it.name }) { entry ->
                        Chip(entry.display, muscle == entry) {
                            muscle = if (muscle == entry) null else entry
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            grouped.forEach { (group, exercises) ->
                item {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader("${group.display} · ${exercises.size}")
                }
                items(exercises.size) { index ->
                    val exercise = exercises[index]
                    ArcCard(
                        Modifier.padding(bottom = 8.dp),
                        onClick = { onOpenExercise(exercise.id) },
                        padding = PaddingValues(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    exercise.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (exercise.archived) W.Faint else W.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (exercise.detail.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        exercise.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = W.Faint,
                                    )
                                }
                            }
                            if (exercise.archived) Pill("ARCHIVED", color = W.Warn)
                            else if (exercise.custom) Pill("YOURS", color = W.Gold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Edit",
                                style = MaterialTheme.typography.labelSmall,
                                color = W.Faint,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { editing = exercise }
                                    .padding(horizontal = 9.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    if (showArchived) "Hide archived" else "Show archived",
                    { showArchived = !showArchived },
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                GoldButton(
                    "NEW MOVEMENT",
                    { creating = true },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Add,
                )
            }
        }
    }

    if (creating || editing != null) {
        ExerciseEditorSheet(
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { exercise ->
                onUpdate {
                    if (editing == null) Actions.addExercise(it, exercise)
                    else Actions.updateExercise(it, exercise)
                }
                creating = false
                editing = null
            },
            onArchive = { id, archived ->
                onUpdate { Actions.archiveExercise(it, id, archived) }
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun ExerciseEditorSheet(
    existing: Exercise?,
    onDismiss: () -> Unit,
    onSave: (Exercise) -> Unit,
    onArchive: (String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var detail by remember { mutableStateOf(existing?.detail ?: "") }
    var muscle by remember { mutableStateOf(existing?.muscle ?: Muscle.CHEST) }
    var equipment by remember { mutableStateOf(existing?.equipment ?: Equipment.BARBELL) }

    WinterSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 540.dp)) {
            SheetTitle(
                if (existing == null) "New movement" else "Edit movement",
                "Anything you add here can be used in any routine or session.",
            )
            WinterField(name, { name = it }, "Name")
            Spacer(Modifier.height(12.dp))
            WinterField(detail, { detail = it }, "Detail (e.g. \"Triceps long head\")")

            Spacer(Modifier.height(18.dp))
            Label("Muscle")
            Spacer(Modifier.height(9.dp))
            FlowChips(
                options = Muscle.entries.map { it.display },
                selectedIndex = Muscle.entries.indexOf(muscle),
                onSelect = { muscle = Muscle.entries[it] },
            )

            Spacer(Modifier.height(18.dp))
            Label("Equipment")
            Spacer(Modifier.height(9.dp))
            FlowChips(
                options = Equipment.entries.map { it.display },
                selectedIndex = Equipment.entries.indexOf(equipment),
                onSelect = { equipment = Equipment.entries[it] },
            )

            Spacer(Modifier.height(20.dp))
            GoldButton(
                "SAVE",
                {
                    if (name.isNotBlank()) {
                        onSave(
                            Exercise(
                                id = existing?.id ?: newId(),
                                name = name.trim(),
                                muscle = muscle,
                                detail = detail.trim(),
                                equipment = equipment,
                                bodyweight = equipment == Equipment.BODYWEIGHT,
                                custom = existing?.custom ?: true,
                                archived = existing?.archived ?: false,
                            ),
                        )
                    }
                },
                Modifier.fillMaxWidth(),
            )

            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                GhostButton(
                    if (existing.archived) "Restore to the library" else "Archive this movement",
                    { onArchive(existing.id, !existing.archived) },
                    Modifier.fillMaxWidth(),
                    color = if (existing.archived) W.Good else W.Warn,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Archiving hides it from pickers. Every past performance of it is kept — " +
                        "deleting it outright would orphan your own history.",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** A simple wrapping chip group. Rows are chunked rather than measured, which is enough here. */
@Composable
private fun FlowChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    options.chunked(3).forEachIndexed { rowIndex, row ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            row.forEachIndexed { columnIndex, option ->
                val index = rowIndex * 3 + columnIndex
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (index == selectedIndex) W.Gold else W.Void.copy(alpha = 0.5f))
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == selectedIndex) W.Void else W.Muted,
                        maxLines = 1,
                    )
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) W.Void else W.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) W.Gold else W.Void.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}
