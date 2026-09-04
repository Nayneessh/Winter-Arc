package com.winterarc.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winterarc.app.AppContainer
import com.winterarc.app.ui.components.*
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProgrammeUiState(
    val templates: List<WorkoutTemplate> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val editing: WorkoutTemplate? = null,
    val loading: Boolean = true,
)

/**
 * Editing the PLAN.
 *
 * Nothing here touches history. Changing a template alters what future sessions start from;
 * sessions that already exist keep the snapshot they were created with.
 */
class ProgrammeViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val _state = MutableStateFlow(ProgrammeUiState())
    val state: StateFlow<ProgrammeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val programme = repo.activeProgramme()
            val templates = programme?.let { p ->
                repo.observeTemplatesOnce(p.id)
            } ?: emptyList()
            val exercises = repo.allExercises()
            _state.value = ProgrammeUiState(
                templates = templates.sortedBy { it.position },
                exercises = exercises,
                names = exercises.associate { it.id to it.name },
                loading = false,
            )
        }
    }

    fun edit(templateId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(editing = repo.templateWithExercises(templateId))
        }
    }

    fun closeEditor() { _state.value = _state.value.copy(editing = null); refresh() }

    fun saveTemplate(template: WorkoutTemplate) {
        viewModelScope.launch {
            repo.saveTemplate(template)
            _state.value = _state.value.copy(editing = template)
            refresh()
        }
    }

    fun saveExercise(exercise: Exercise) {
        viewModelScope.launch { repo.saveExercise(exercise); refresh() }
    }

    fun archiveExercise(id: String) {
        viewModelScope.launch { repo.archiveExercise(id); refresh() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProgrammeViewModel(container) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    state: ProgrammeUiState,
    onSave: (Exercise) -> Unit,
    onArchive: (String) -> Unit,
    onBack: () -> Unit,
    newId: () -> String,
) {
    var showNew by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = { Text("Exercise library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = WinterArcColors.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WinterArcColors.NightDeep,
                    titleContentColor = WinterArcColors.White,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                GoldButton("New", { showNew = true })
            }

            val filtered = state.exercises.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true)
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { ex ->
                    WinterCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${ex.primaryMuscle.label} · ${ex.equipment.label} · " +
                                        "${ex.defaultRestSeconds}s rest",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WinterArcColors.Muted,
                                )
                                ex.notes?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WinterArcColors.Faint,
                                    )
                                }
                            }
                            if (ex.isCustom) Chip("Custom", color = WinterArcColors.Gold)
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewExerciseDialog(
            onDismiss = { showNew = false },
            onCreate = { name, muscle, equip, rest, notes ->
                onSave(
                    Exercise(
                        id = newId(),
                        name = name,
                        primaryMuscle = muscle,
                        equipment = equip,
                        defaultRestSeconds = rest,
                        notes = notes,
                        isCustom = true,
                    ),
                )
                showNew = false
            },
        )
    }
}

@Composable
private fun NewExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (String, MuscleGroup, Equipment, Int, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    var equipment by remember { mutableStateOf(Equipment.BARBELL) }
    var rest by remember { mutableStateOf("90") }
    var notes by remember { mutableStateOf("") }
    var muscleOpen by remember { mutableStateOf(false) }
    var equipOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinterArcColors.NightElevated,
        title = { Text("New exercise", color = WinterArcColors.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                DropdownField("Muscle", muscle.label, muscleOpen, { muscleOpen = it }) {
                    MuscleGroup.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label, color = WinterArcColors.White) },
                            onClick = { muscle = m; muscleOpen = false },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                DropdownField("Equipment", equipment.label, equipOpen, { equipOpen = it }) {
                    Equipment.entries.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.label, color = WinterArcColors.White) },
                            onClick = { equipment = e; equipOpen = false },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rest,
                    onValueChange = { rest = it.filter(Char::isDigit).take(4) },
                    label = { Text("Default rest (s)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        name.trim(), muscle, equipment,
                        rest.toIntOrNull()?.coerceIn(0, 3600) ?: 90,
                        notes.takeIf { it.isNotBlank() },
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Create", color = WinterArcColors.Gold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WinterArcColors.Muted) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            items()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    state: ProgrammeUiState,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val dayNames = listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = { Text("Workout templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = WinterArcColors.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WinterArcColors.NightDeep,
                    titleContentColor = WinterArcColors.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Editing a template changes what future workouts start from. Sessions you " +
                        "have already completed keep exactly what they recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Faint,
                )
            }
            items(state.templates, key = { it.id }) { t ->
                WinterCard(onClick = { onEdit(t.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    t.dayOfWeek?.let { append(dayNames.getOrElse(it) { "" }) }
                                    append("  ·  ${t.dayType.name.lowercase().replace('_', ' ')}")
                                    if (t.exercises.isNotEmpty()) {
                                        append("  ·  ${t.exercises.size} exercises, ${t.plannedSetCount} sets")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = WinterArcColors.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    template: WorkoutTemplate,
    names: Map<String, String>,
    library: List<Exercise>,
    onSave: (WorkoutTemplate) -> Unit,
    onBack: () -> Unit,
    newId: () -> String,
) {
    var working by remember(template.id) { mutableStateOf(template) }
    var showAdd by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    fun commit(next: WorkoutTemplate) { working = next; onSave(next) }

    fun reindex(list: List<PlannedExercise>) = list.mapIndexed { i, p -> p.copy(position = i) }

    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = { Text(working.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = WinterArcColors.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WinterArcColors.NightDeep,
                    titleContentColor = WinterArcColors.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = working.name,
                    onValueChange = { commit(working.copy(name = it)) },
                    label = { Text("Workout name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { GoldButton("+ Add exercise", { showAdd = true }, Modifier.fillMaxWidth()) }

            itemsIndexed2(working.exercises.sortedBy { it.position }) { index, pe ->
                WinterCard(onClick = { editingIndex = index }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                pe.supersetGroup?.let {
                                    Chip(it, color = WinterArcColors.NightBlueBright)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    names[pe.exerciseId] ?: "Exercise",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Text(
                                "${pe.sets} × ${pe.repRangeLabel}" +
                                    (pe.targetWeightKg?.takeIf { it > 0 }?.let { " @ $it kg" } ?: "") +
                                    "  ·  ${pe.restSeconds}s rest",
                                style = MaterialTheme.typography.bodySmall,
                                color = WinterArcColors.Muted,
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val list = working.exercises.sortedBy { it.position }.toMutableList()
                                    val item = list.removeAt(index)
                                    list.add(index - 1, item)
                                    commit(working.copy(exercises = reindex(list)))
                                }
                            },
                            enabled = index > 0,
                        ) { Icon(Icons.Default.ArrowUpward, "Up", tint = WinterArcColors.Muted) }
                        IconButton(
                            onClick = {
                                val list = working.exercises.sortedBy { it.position }.toMutableList()
                                if (index < list.lastIndex) {
                                    val item = list.removeAt(index)
                                    list.add(index + 1, item)
                                    commit(working.copy(exercises = reindex(list)))
                                }
                            },
                            enabled = index < working.exercises.lastIndex,
                        ) { Icon(Icons.Default.ArrowDownward, "Down", tint = WinterArcColors.Muted) }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = WinterArcColors.NightElevated,
            title = { Text("Add to ${working.name}", color = WinterArcColors.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("Search") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(
                            library.filter { query.isBlank() || it.name.contains(query, true) },
                            key = { it.id },
                        ) { ex ->
                            TextButton(
                                onClick = {
                                    val next = working.exercises + PlannedExercise(
                                        id = newId(),
                                        exerciseId = ex.id,
                                        position = working.exercises.size,
                                        sets = 3, repLow = 8, repHigh = 12,
                                        restSeconds = ex.defaultRestSeconds,
                                    )
                                    commit(working.copy(exercises = next))
                                    showAdd = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(ex.name, color = WinterArcColors.White, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdd = false }) { Text("Close", color = WinterArcColors.Muted) }
            },
        )
    }

    editingIndex?.let { index ->
        val pe = working.exercises.sortedBy { it.position }.getOrNull(index)
        if (pe != null) {
            EditPlannedDialog(
                planned = pe,
                name = names[pe.exerciseId] ?: "Exercise",
                onDismiss = { editingIndex = null },
                onDelete = {
                    val list = working.exercises.sortedBy { it.position }.toMutableList()
                    list.removeAt(index)
                    commit(working.copy(exercises = reindex(list)))
                    editingIndex = null
                },
                onSave = { updated ->
                    commit(
                        working.copy(
                            exercises = working.exercises.map { if (it.id == updated.id) updated else it },
                        ),
                    )
                    editingIndex = null
                },
            )
        }
    }
}

@Composable
private fun EditPlannedDialog(
    planned: PlannedExercise,
    name: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (PlannedExercise) -> Unit,
) {
    var sets by remember { mutableStateOf(planned.sets.toString()) }
    var low by remember { mutableStateOf(planned.repLow.toString()) }
    var high by remember { mutableStateOf(planned.repHigh.toString()) }
    var weight by remember { mutableStateOf(planned.targetWeightKg?.toString() ?: "") }
    var rest by remember { mutableStateOf(planned.restSeconds.toString()) }
    var superset by remember { mutableStateOf(planned.supersetGroup ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinterArcColors.NightElevated,
        title = { Text(name, color = WinterArcColors.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Num("Sets", sets, Modifier.weight(1f)) { sets = it }
                    Num("Rep low", low, Modifier.weight(1f)) { low = it }
                    Num("Rep high", high, Modifier.weight(1f)) { high = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Num("Weight kg", weight, Modifier.weight(1f), decimal = true) { weight = it }
                    Num("Rest s", rest, Modifier.weight(1f)) { rest = it }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = superset,
                    onValueChange = { superset = it.take(2).uppercase() },
                    label = { Text("Superset group (A, B, …)") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            "Exercises sharing a letter are performed together. Leave blank for none.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete) {
                    Text("Remove from this workout", color = WinterArcColors.Danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lowV = low.toIntOrNull()?.coerceAtLeast(1) ?: planned.repLow
                onSave(
                    planned.copy(
                        sets = sets.toIntOrNull()?.coerceAtLeast(1) ?: planned.sets,
                        repLow = lowV,
                        repHigh = high.toIntOrNull()?.coerceAtLeast(lowV) ?: planned.repHigh,
                        targetWeightKg = weight.toDoubleOrNull(),
                        restSeconds = rest.toIntOrNull()?.coerceAtLeast(0) ?: planned.restSeconds,
                        supersetGroup = superset.takeIf { it.isNotBlank() },
                    ),
                )
            }) { Text("Save", color = WinterArcColors.Gold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WinterArcColors.Muted) }
        },
    )
}

@Composable
private fun Num(
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

/** Local helper: LazyListScope.itemsIndexed with a stable key derived from the planned id. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed2(
    list: List<PlannedExercise>,
    content: @Composable (Int, PlannedExercise) -> Unit,
) = androidx.compose.foundation.lazy.itemsIndexed(
    items = list,
    key = { _, item -> item.id },
) { index, item -> content(index, item) }
