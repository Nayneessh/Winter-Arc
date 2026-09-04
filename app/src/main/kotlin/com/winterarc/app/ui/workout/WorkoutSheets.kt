package com.winterarc.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.components.Chip
import com.winterarc.app.ui.components.GoldButton
import com.winterarc.app.ui.components.OutlineButton
import com.winterarc.app.ui.components.SectionLabel
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.model.Equipment
import com.winterarc.domain.model.Exercise
import com.winterarc.domain.model.MuscleGroup
import com.winterarc.domain.model.PerformedExercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WinterArcColors.NightSurface,
        contentColor = WinterArcColors.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            content = content,
        )
    }
}

/**
 * Adds a movement to the current session — either from the library, or by creating a new one.
 *
 * Creating is offered inline rather than behind a separate screen because discovering a new
 * exercise mid-session is a normal event, and forcing a detour to a settings page to record
 * it would mean it simply does not get recorded.
 */
@Composable
fun AddExerciseSheet(
    library: List<Exercise>,
    onDismiss: () -> Unit,
    onPick: (String, Int, Int, Int, Int) -> Unit,
    onCreate: (String, MuscleGroup, Equipment, Int, String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    Sheet(onDismiss) {
        if (!creating) {
            Text("Add exercise", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlineButton("+ Create a new exercise", { creating = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            val filtered = library.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                    it.primaryMuscle.label.contains(query, ignoreCase = true)
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.id }) { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WinterArcColors.NightElevated)
                            .clickable { onPick(ex.id, 3, 8, 12, ex.defaultRestSeconds) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${ex.primaryMuscle.label} · ${ex.equipment.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WinterArcColors.Muted,
                            )
                        }
                        if (ex.isCustom) Chip("Custom", color = WinterArcColors.Gold)
                    }
                }
            }
        } else {
            CreateExerciseForm(
                existingNames = library.map { it.name },
                onCancel = { creating = false },
                onCreate = onCreate,
            )
        }
    }
}

@Composable
private fun CreateExerciseForm(
    existingNames: List<String>,
    onCancel: () -> Unit,
    onCreate: (String, MuscleGroup, Equipment, Int, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    var equipment by remember { mutableStateOf(Equipment.BARBELL) }
    var rest by remember { mutableStateOf("90") }
    var notes by remember { mutableStateOf("") }

    // A duplicate name is allowed but flagged: two gyms genuinely have differently loaded
    // machines with the same label, and silently blocking that would be wrong.
    val duplicate = existingNames.any { it.equals(name.trim(), ignoreCase = true) }

    Text("New exercise", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        singleLine = true,
        isError = duplicate,
        supportingText = if (duplicate) {
            { Text("You already have an exercise with this name.", color = WinterArcColors.Warning) }
        } else null,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))
    SectionLabel("Primary muscle")
    EnumChips(MuscleGroup.entries.toList(), muscle, { it.label }) { muscle = it }

    Spacer(Modifier.height(12.dp))
    SectionLabel("Equipment")
    EnumChips(Equipment.entries.toList(), equipment, { it.label }) { equipment = it }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = rest,
        onValueChange = { rest = it.filter(Char::isDigit).take(4) },
        label = { Text("Default rest (seconds)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlineButton("Cancel", onCancel, Modifier.weight(1f))
        GoldButton(
            text = "Create & add",
            onClick = {
                onCreate(
                    name.trim(), muscle, equipment,
                    rest.toIntOrNull()?.coerceIn(0, 3600) ?: 90,
                    notes.takeIf { it.isNotBlank() },
                )
            },
            modifier = Modifier.weight(1f),
            enabled = name.isNotBlank(),
        )
    }
}

@Composable
private fun <T> EnumChips(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRowSimple {
        values.forEach { v ->
            val isSelected = v == selected
            Text(
                text = label(v),
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) WinterArcColors.NightDeep else WinterArcColors.White,
                modifier = Modifier
                    .padding(end = 6.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) WinterArcColors.Gold else WinterArcColors.NightElevated)
                    .clickable { onSelect(v) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

/** Wrapping row for chip groups. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSimple(content: @Composable () -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
fun ReplaceExerciseSheet(
    library: List<Exercise>,
    currentName: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Sheet(onDismiss) {
        Text("Replace $currentName", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Applies to this workout only. Past sessions and the saved plan are untouched.",
            style = MaterialTheme.typography.bodySmall,
            color = WinterArcColors.Muted,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        val filtered = library.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.id }) { ex ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WinterArcColors.NightElevated)
                        .clickable { onPick(ex.id) }
                        .padding(14.dp),
                ) {
                    Column {
                        Text(ex.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${ex.primaryMuscle.label} · ${ex.equipment.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WinterArcColors.Muted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reordering.
 *
 * Uses explicit up/down controls rather than free drag. A drag gesture on a small, sweaty
 * touch target between sets misfires often enough to be a liability; two large arrows are
 * slower per action but never reorder the wrong exercise.
 */
@Composable
fun ReorderSheet(
    items: List<String>,
    onDismiss: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Sheet(onDismiss) {
        Text("Reorder exercises", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(items) { index, name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WinterArcColors.NightElevated)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.labelMedium,
                        color = WinterArcColors.Gold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = { if (index > 0) onMove(index, index - 1) },
                        enabled = index > 0,
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward, "Move up",
                            tint = if (index > 0) WinterArcColors.White else WinterArcColors.Faint,
                        )
                    }
                    IconButton(
                        onClick = { if (index < items.lastIndex) onMove(index, index + 1) },
                        enabled = index < items.lastIndex,
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward, "Move down",
                            tint = if (index < items.lastIndex) WinterArcColors.White else WinterArcColors.Faint,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GoldButton("Done", onDismiss, Modifier.fillMaxWidth())
    }
}

/** Adjusts the target for the current session without editing the saved template. */
@Composable
fun EditPlanSheet(
    performed: PerformedExercise,
    onDismiss: () -> Unit,
    onSave: (Int?, Int?, Int?, Double?, Int?) -> Unit,
) {
    var sets by remember { mutableStateOf(performed.plannedSets.toString()) }
    var low by remember { mutableStateOf(performed.plannedRepLow.toString()) }
    var high by remember { mutableStateOf(performed.plannedRepHigh.toString()) }
    var weight by remember { mutableStateOf(performed.plannedWeightKg?.toString() ?: "") }
    var rest by remember { mutableStateOf(performed.plannedRestSeconds.toString()) }

    Sheet(onDismiss) {
        Text("Edit target", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Changes apply to this workout. Your saved plan stays as it is.",
            style = MaterialTheme.typography.bodySmall,
            color = WinterArcColors.Muted,
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Sets", sets, Modifier.weight(1f)) { sets = it }
            NumField("Rep low", low, Modifier.weight(1f)) { low = it }
            NumField("Rep high", high, Modifier.weight(1f)) { high = it }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Weight (kg)", weight, Modifier.weight(1f), decimal = true) { weight = it }
            NumField("Rest (s)", rest, Modifier.weight(1f)) { rest = it }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("Cancel", onDismiss, Modifier.weight(1f))
            GoldButton(
                text = "Save",
                onClick = {
                    val lowV = low.toIntOrNull()?.coerceAtLeast(1)
                    val highV = high.toIntOrNull()?.coerceAtLeast(lowV ?: 1)
                    onSave(
                        sets.toIntOrNull()?.coerceAtLeast(1),
                        lowV,
                        highV,
                        weight.toDoubleOrNull(),
                        rest.toIntOrNull()?.coerceAtLeast(0),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onChange(if (decimal) input.filter { it.isDigit() || it == '.' } else input.filter(Char::isDigit))
        },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}
