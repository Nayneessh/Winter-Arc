package com.winterarc.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.components.*
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.model.*
import kotlin.math.roundToInt

private fun Double.trim(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else "%.1f".format(this)

/**
 * The screen used while actually training.
 *
 * Priorities, in order: the current set must be loggable in one tap; the prescribed target
 * and the previous session's result must both be visible without scrolling; everything else
 * is secondary. Controls are oversized because this is operated with chalky, sweaty hands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    timer: TimerState,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onLogSet: (String, Double, Int, Boolean) -> Unit,
    onUpdateSet: (String, Double?, Int?) -> Unit,
    onRemoveSet: (String) -> Unit,
    onSelectExercise: (Int) -> Unit,
    onReplaceExercise: (String, String) -> Unit,
    onAddExercise: (String, Int, Int, Int, Int) -> Unit,
    onCreateExercise: (String, MuscleGroup, Equipment, Int, String?) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onSkipExercise: (String, Boolean) -> Unit,
    onMoveExercise: (Int, Int) -> Unit,
    onAdjustPlan: (String, Int?, Int?, Int?, Double?, Int?) -> Unit,
    onStartTimer: (Int, String) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onSkipTimer: () -> Unit,
    onAdjustTimer: (Int) -> Unit,
    onTimerFinishedAck: () -> Unit,
    onFinish: () -> Unit,
    onAbandon: () -> Unit,
    onBack: () -> Unit,
) {
    // The timer takes the whole screen: during rest there is nothing else to look at.
    if (timer.isActive) {
        RestTimerOverlay(
            state = timer,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            onPause = onPauseTimer,
            onResume = onResumeTimer,
            onSkip = onSkipTimer,
            onAdjust = onAdjustTimer,
            onFinishedAcknowledged = onTimerFinishedAck,
        )
        return
    }

    val session = state.session
    var showAddSheet by remember { mutableStateOf(false) }
    var showReplaceSheet by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var showPlanSheet by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showAbandonDialog by remember { mutableStateOf(false) }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WinterArcColors.Gold)
        }
        return
    }

    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No workout in progress",
                message = state.message ?: "Start one from the home screen.",
            )
        }
        return
    }

    val ordered = session.exercises.sortedBy { it.position }
    val current = state.current

    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${session.actualSetTotal} of ${session.plannedSetTotal} sets" +
                                if (session.extraSetTotal > 0) "  ·  +${session.extraSetTotal} extra" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = WinterArcColors.Muted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = WinterArcColors.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showReorderSheet = true }) {
                        Icon(Icons.Default.SwapVert, "Reorder", tint = WinterArcColors.White)
                    }
                    IconButton(onClick = { showAbandonDialog = true }) {
                        Icon(Icons.Default.Close, "Discard", tint = WinterArcColors.Muted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WinterArcColors.NightDeep,
                    titleContentColor = WinterArcColors.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Exercise rail — position within the session is always visible.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                itemsIndexed(ordered) { index, pe ->
                    val done = pe.actualSetCount >= pe.plannedSets && pe.plannedSets > 0
                    ExercisePill(
                        label = state.nameOf(pe.exerciseId),
                        index = index + 1,
                        selected = index == state.currentIndex,
                        complete = done,
                        skipped = pe.isSkipped,
                        onClick = { onSelectExercise(index) },
                    )
                }
                item {
                    ExercisePill(
                        label = "Add",
                        index = 0,
                        selected = false,
                        complete = false,
                        skipped = false,
                        isAdd = true,
                        onClick = { showAddSheet = true },
                    )
                }
            }

            if (current == null) {
                EmptyState(
                    title = "No exercises yet",
                    message = "Add the first movement to begin logging.",
                )
                return@Column
            }

            val previous = state.previousPerformance[current.exerciseId]

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    CurrentExerciseCard(
                        name = state.nameOf(current.exerciseId),
                        performed = current,
                        previous = previous,
                        previousName = previous?.let { state.nameOf(it.exerciseId) },
                        replacedName = current.replacedExerciseId?.let { state.nameOf(it) },
                        onEditPlan = { showPlanSheet = true },
                        onReplace = { showReplaceSheet = true },
                        onSkip = { onSkipExercise(current.id, !current.isSkipped) },
                        onRemove = { onRemoveExercise(current.id) },
                    )
                }

                item {
                    SetLogger(
                        performed = current,
                        onLog = { w, r, warm -> onLogSet(current.id, w, r, warm) },
                    )
                }

                if (current.sets.isNotEmpty()) {
                    item { SectionLabel("Sets performed") }
                    items(current.sets.sortedBy { it.setNumber }, key = { it.id }) { set ->
                        SetRow(
                            set = set,
                            isExtra = set.setNumber > current.plannedSets && !set.isWarmup,
                            onRemove = { onRemoveSet(set.id) },
                            onAdjust = { w, r -> onUpdateSet(set.id, w, r) },
                        )
                    }
                }

                item {
                    OutlineButton(
                        text = "Start rest timer (${current.plannedRestSeconds}s)",
                        onClick = {
                            onStartTimer(current.plannedRestSeconds, state.nameOf(current.exerciseId))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item { Spacer(Modifier.height(72.dp)) }
            }

            Surface(color = WinterArcColors.NightSurface, tonalElevation = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlineButton("Previous", { onSelectExercise((state.currentIndex - 1).coerceAtLeast(0)) }, Modifier.weight(1f))
                    if (state.currentIndex >= ordered.lastIndex) {
                        GoldButton("FINISH", { showFinishDialog = true }, Modifier.weight(1f))
                    } else {
                        GoldButton("Next", { onSelectExercise(state.currentIndex + 1) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddExerciseSheet(
            library = state.library,
            onDismiss = { showAddSheet = false },
            onPick = { id, sets, low, high, rest ->
                onAddExercise(id, sets, low, high, rest); showAddSheet = false
            },
            onCreate = { name, muscle, equip, rest, notes ->
                onCreateExercise(name, muscle, equip, rest, notes); showAddSheet = false
            },
        )
    }

    if (showReplaceSheet && current != null) {
        ReplaceExerciseSheet(
            library = state.library,
            currentName = state.nameOf(current.exerciseId),
            onDismiss = { showReplaceSheet = false },
            onPick = { onReplaceExercise(current.id, it); showReplaceSheet = false },
        )
    }

    if (showReorderSheet) {
        ReorderSheet(
            items = ordered.map { state.nameOf(it.exerciseId) },
            onDismiss = { showReorderSheet = false },
            onMove = onMoveExercise,
        )
    }

    if (showPlanSheet && current != null) {
        EditPlanSheet(
            performed = current,
            onDismiss = { showPlanSheet = false },
            onSave = { sets, low, high, weight, rest ->
                onAdjustPlan(current.id, sets, low, high, weight, rest); showPlanSheet = false
            },
        )
    }

    if (showFinishDialog) {
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            containerColor = WinterArcColors.NightElevated,
            title = { Text("Finish workout?", color = WinterArcColors.White) },
            text = {
                Column {
                    Text(
                        "${session.actualSetTotal} sets · ${session.totalReps} reps · " +
                            "${session.totalVolumeKg.roundToInt()} kg volume",
                        color = WinterArcColors.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Session notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFinishDialog = false; onFinish() }) {
                    Text("Finish", color = WinterArcColors.Gold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Keep going", color = WinterArcColors.Muted)
                }
            },
        )
    }

    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            containerColor = WinterArcColors.NightElevated,
            title = { Text("Discard this workout?", color = WinterArcColors.White) },
            text = {
                Text(
                    "Everything logged so far will be discarded. This cannot be undone.",
                    color = WinterArcColors.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbandonDialog = false; onAbandon(); onBack() }) {
                    Text("Discard", color = WinterArcColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonDialog = false }) {
                    Text("Cancel", color = WinterArcColors.Muted)
                }
            },
        )
    }
}

@Composable
private fun ExercisePill(
    label: String,
    index: Int,
    selected: Boolean,
    complete: Boolean,
    skipped: Boolean,
    isAdd: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        selected -> WinterArcColors.Gold
        complete -> WinterArcColors.NightElevated
        else -> WinterArcColors.NightSurface
    }
    val fg = when {
        selected -> WinterArcColors.NightDeep
        skipped -> WinterArcColors.Faint
        complete -> WinterArcColors.Success
        else -> WinterArcColors.White
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isAdd) {
            Icon(Icons.Default.Add, null, tint = WinterArcColors.Gold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        } else if (complete) {
            Icon(Icons.Default.Check, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = if (isAdd) label else "$index. ${label.take(18)}",
            style = MaterialTheme.typography.labelMedium,
            color = if (isAdd) WinterArcColors.Gold else fg,
        )
    }
}

@Composable
private fun CurrentExerciseCard(
    name: String,
    performed: PerformedExercise,
    previous: PerformedExercise?,
    previousName: String?,
    replacedName: String?,
    onEditPlan: () -> Unit,
    onReplace: () -> Unit,
    onSkip: () -> Unit,
    onRemove: () -> Unit,
) {
    WinterCard(accent = true) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.headlineSmall, color = WinterArcColors.White)
                if (replacedName != null) {
                    Text(
                        "Replaced $replacedName for this workout",
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.NightBlueBright,
                    )
                }
                if (performed.isAdHoc) {
                    Text(
                        "Added to this workout",
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.NightBlueBright,
                    )
                }
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Options", tint = WinterArcColors.Muted)
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.background(WinterArcColors.NightElevated),
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit target", color = WinterArcColors.White) },
                        onClick = { menu = false; onEditPlan() },
                    )
                    DropdownMenuItem(
                        text = { Text("Replace exercise", color = WinterArcColors.White) },
                        onClick = { menu = false; onReplace() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (performed.isSkipped) "Un-skip" else "Skip this exercise",
                                color = WinterArcColors.White,
                            )
                        },
                        onClick = { menu = false; onSkip() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from workout", color = WinterArcColors.Danger) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text("TARGET", style = MaterialTheme.typography.labelSmall, color = WinterArcColors.Muted)
                Text(
                    buildString {
                        append("${performed.plannedSets} × ")
                        append(
                            if (performed.plannedRepLow == performed.plannedRepHigh) {
                                "${performed.plannedRepLow}"
                            } else {
                                "${performed.plannedRepLow}-${performed.plannedRepHigh}"
                            },
                        )
                        performed.plannedWeightKg?.takeIf { it > 0 }?.let { append(" @ ${it.trim()} kg") }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = WinterArcColors.Gold,
                )
            }
            Column {
                Text("DONE", style = MaterialTheme.typography.labelSmall, color = WinterArcColors.Muted)
                Text(
                    "${performed.actualSetCount} sets" +
                        if (performed.extraSetCount > 0) " (+${performed.extraSetCount})" else "",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (performed.extraSetCount > 0) WinterArcColors.Success else WinterArcColors.White,
                )
            }
        }

        if (previous != null && previous.workingSets.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val best = previous.heaviestSet
            Text(
                text = "Last time" + (if (previousName != null && previousName != name) " ($previousName)" else "") +
                    ": " + previous.workingSets.joinToString("  ") { "${it.weightKg.trim()}×${it.reps}" },
                style = MaterialTheme.typography.bodySmall,
                color = WinterArcColors.Muted,
            )
            if (best != null) {
                Text(
                    "Best set ${best.weightKg.trim()} kg × ${best.reps}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Faint,
                )
            }
        }

        if (performed.plannedStyle != TrainingStyle.UNSPECIFIED) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(performed.plannedStyle.label, color = WinterArcColors.Gold)
                Chip("${performed.plannedRestSeconds}s rest")
                performed.supersetGroup?.let { Chip("Superset $it", color = WinterArcColors.NightBlueBright) }
            }
        }
    }
}

/**
 * The primary logging control.
 *
 * Weight and reps pre-fill from the previous set (or the prescribed target), so the common
 * case — repeating the same set — is a single tap on COMPLETE SET.
 */
@Composable
private fun SetLogger(
    performed: PerformedExercise,
    onLog: (Double, Int, Boolean) -> Unit,
) {
    val lastSet = performed.sets.maxByOrNull { it.setNumber }
    var weight by remember(performed.id, performed.sets.size) {
        mutableStateOf(lastSet?.weightKg ?: performed.plannedWeightKg ?: 0.0)
    }
    var reps by remember(performed.id, performed.sets.size) {
        mutableStateOf(lastSet?.reps ?: performed.plannedRepHigh)
    }
    var warmup by remember(performed.id) { mutableStateOf(false) }
    var editWeight by remember { mutableStateOf(false) }
    var editReps by remember { mutableStateOf(false) }

    val nextSetNumber = (performed.sets.maxOfOrNull { it.setNumber } ?: 0) + 1
    val isExtra = !warmup && nextSetNumber > performed.plannedSets

    WinterCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (warmup) "WARM-UP SET" else "SET $nextSetNumber",
                style = MaterialTheme.typography.labelMedium,
                color = if (isExtra) WinterArcColors.Success else WinterArcColors.Muted,
            )
            if (isExtra) Chip("EXTRA", color = WinterArcColors.Success)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NumberStepper(
                label = "Weight",
                value = weight.trim(),
                suffix = "kg",
                onDecrement = { weight = (weight - 2.5).coerceAtLeast(0.0) },
                onIncrement = { weight += 2.5 },
                onValueClick = { editWeight = true },
                modifier = Modifier.weight(1f),
            )
            NumberStepper(
                label = "Reps",
                value = reps.toString(),
                onDecrement = { reps = (reps - 1).coerceAtLeast(0) },
                onIncrement = { reps += 1 },
                onValueClick = { editReps = true },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        GoldButton(
            text = if (warmup) "LOG WARM-UP" else "COMPLETE SET",
            onClick = { onLog(weight, reps, warmup); warmup = false },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlineButton(
                text = if (warmup) "Working set" else "Warm-up",
                onClick = { warmup = !warmup },
                modifier = Modifier.weight(1f),
            )
            OutlineButton(
                text = "+ Add set",
                onClick = { onLog(weight, reps, false) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (editWeight) {
        NumericDialog("Weight (kg)", weight.trim(), true, { editWeight = false }) {
            weight = it.toDoubleOrNull()?.coerceAtLeast(0.0) ?: weight
            editWeight = false
        }
    }
    if (editReps) {
        NumericDialog("Reps", reps.toString(), false, { editReps = false }) {
            reps = it.toIntOrNull()?.coerceIn(0, 999) ?: reps
            editReps = false
        }
    }
}

@Composable
private fun NumericDialog(
    label: String,
    initial: String,
    decimal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinterArcColors.NightElevated,
        title = { Text(label, color = WinterArcColors.White) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Set", color = WinterArcColors.Gold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WinterArcColors.Muted) }
        },
    )
}

@Composable
private fun SetRow(
    set: ActualSet,
    isExtra: Boolean,
    onRemove: () -> Unit,
    onAdjust: (Double?, Int?) -> Unit,
) {
    WinterCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            set.isWarmup -> WinterArcColors.NightBlue
                            isExtra -> WinterArcColors.Success.copy(alpha = 0.25f)
                            else -> WinterArcColors.NightElevated
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (set.isWarmup) "W" else set.setNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isExtra) WinterArcColors.Success else WinterArcColors.White,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${set.weightKg.trim()} kg × ${set.reps}",
                    style = MaterialTheme.typography.titleMedium,
                    color = WinterArcColors.White,
                )
                Text(
                    buildString {
                        append("${set.volumeKg.roundToInt()} kg volume")
                        if (set.isWarmup) append("  ·  warm-up, excluded")
                        if (isExtra) append("  ·  extra set")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Faint,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.DeleteOutline, "Remove set", tint = WinterArcColors.Muted)
            }
        }
    }
}
