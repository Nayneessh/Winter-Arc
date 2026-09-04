package com.winterarc.app.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.GoldButton
import com.winterarc.app.ui.kit.HairLine
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.NumberPadDialog
import com.winterarc.app.ui.kit.Pill
import com.winterarc.app.ui.kit.RoundIcon
import com.winterarc.app.ui.kit.SheetAction
import com.winterarc.app.ui.kit.SheetTitle
import com.winterarc.app.ui.kit.WinterSheet
import com.winterarc.app.ui.plan.ExercisePickerSheet
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Actions
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import com.winterarc.core.Priority
import com.winterarc.core.SessionExercise
import com.winterarc.core.SetEntry
import kotlinx.coroutines.delay

/** Which numeric field the keypad is currently editing. */
private sealed interface Editing {
    data class Weight(val exerciseId: String, val setId: String, val value: Double) : Editing
    data class Reps(val exerciseId: String, val setId: String, val value: Int) : Editing
}

/**
 * The workout.
 *
 * Everything here is built for one hand, mid-set, without concentration to spare: large targets,
 * numbers pre-filled from last time, and one obvious tap to record a set and start the rest.
 */
@Composable
fun TrainScreen(
    data: AppData,
    restTimer: RestTimerController,
    onUpdate: ((AppData) -> AppData) -> Unit,
    onClose: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val session = data.activeSession
    var finishedId by remember { mutableStateOf<String?>(null) }

    // Once the session is finished the summary takes over the screen -- the last thing the user
    // did deserves a result, not an instant return to a list.
    val finished = finishedId?.let { id -> data.sessions.firstOrNull { it.id == id } }
    if (finished != null) {
        SessionSummary(data = data, session = finished, onDone = onClose)
        return
    }

    if (session == null) {
        Box(Modifier.fillMaxSize().background(Grad.screen), contentAlignment = Alignment.Center) {
            GhostButton("Back", onClose)
        }
        return
    }

    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(session.id) {
        while (true) {
            elapsed = ((System.currentTimeMillis() - session.startedAtMillis) / 1000L).toInt().coerceAtLeast(0)
            delay(1000)
        }
    }

    RestTimerTicker(restTimer)

    var editing by remember { mutableStateOf<Editing?>(null) }
    var optionsFor by remember { mutableStateOf<String?>(null) }
    var replaceFor by remember { mutableStateOf<String?>(null) }
    var targetFor by remember { mutableStateOf<String?>(null) }
    var setMenuFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var addingExercise by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Grad.screen)) {
        Column(Modifier.fillMaxSize()) {

            // -- header ------------------------------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.Filled.Close, "Leave workout", { confirmDiscard = true })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        session.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = W.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fmt.clock(elapsed),
                            style = MaterialTheme.typography.bodySmall,
                            color = W.accent(session.accent),
                        )
                        Text(
                            "  ·  ${session.exercises.sumOf { it.completedCount }} sets logged  ·  " +
                                "${Fmt.volume(session.volumeKg, data.prefs.unit)} ${data.prefs.unit.suffix}",
                            style = MaterialTheme.typography.bodySmall,
                            color = W.Faint,
                        )
                    }
                }
                Text(
                    "FINISH",
                    style = MaterialTheme.typography.labelMedium,
                    color = W.GoldBright,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(W.GoldFilm)
                        .border(1.dp, W.GoldEdge, RoundedCornerShape(12.dp))
                        .clickable { confirmFinish = true }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }

            // -- exercises ---------------------------------------------------------------------
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                itemsIndexed(session.exercises, key = { _, item -> item.id }) { index, exercise ->
                    val nextShares = session.exercises.getOrNull(index + 1)?.groupLetter == exercise.groupLetter
                    val prevShares = session.exercises.getOrNull(index - 1)?.groupLetter == exercise.groupLetter

                    ExerciseCard(
                        data = data,
                        exercise = exercise,
                        supersetWithNext = nextShares,
                        supersetWithPrevious = prevShares,
                        onEditWeight = { set ->
                            editing = Editing.Weight(exercise.id, set.id, set.weightKg)
                        },
                        onEditReps = { set ->
                            editing = Editing.Reps(exercise.id, set.id, set.reps)
                        },
                        onToggleDone = { set ->
                            val becomingDone = !set.done
                            onUpdate { Actions.toggleSetDone(it, exercise.id, set.id) }
                            if (becomingDone && data.prefs.restTimerAutoStart && exercise.restSeconds > 0) {
                                restTimer.start(
                                    exercise.restSeconds,
                                    data.exerciseName(exercise.exerciseId),
                                )
                            }
                        },
                        onSetMenu = { set -> setMenuFor = exercise.id to set.id },
                        onAddSet = { onUpdate { Actions.addSet(it, exercise.id) } },
                        onOptions = { optionsFor = exercise.id },
                        onStartRest = {
                            restTimer.start(exercise.restSeconds, data.exerciseName(exercise.exerciseId))
                        },
                    )
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    GhostButton(
                        text = "Add an exercise",
                        onClick = { addingExercise = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Add,
                    )
                    Spacer(Modifier.height(80.dp))
                }
            }

            // -- foot --------------------------------------------------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 12.dp),
            ) {
                RestBar(restTimer)
                if (restTimer.visible && !restTimer.expanded) Spacer(Modifier.height(10.dp))
                GoldButton(
                    text = "FINISH SESSION",
                    onClick = { confirmFinish = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Check,
                )
            }
        }

        RestOverlay(restTimer)
    }

    // -- keypad ------------------------------------------------------------------------------
    when (val edit = editing) {
        is Editing.Weight -> NumberPadDialog(
            title = "Weight",
            initial = Fmt.weight(edit.value, data.prefs.unit),
            unit = data.prefs.unit.suffix,
            onDismiss = { editing = null },
            onConfirm = { text ->
                val entered = text.toDoubleOrNull() ?: 0.0
                onUpdate {
                    Actions.updateSet(
                        it, edit.exerciseId, edit.setId,
                        weightKg = Fmt.fromDisplayWeight(entered, data.prefs.unit),
                    )
                }
                editing = null
            },
            quickSteps = listOf(-data.prefs.weightStepKg, -1.0, 1.0, data.prefs.weightStepKg),
        )

        is Editing.Reps -> NumberPadDialog(
            title = "Reps",
            initial = edit.value.toString(),
            unit = "reps",
            onDismiss = { editing = null },
            onConfirm = { text ->
                onUpdate {
                    Actions.updateSet(it, edit.exerciseId, edit.setId, reps = text.toIntOrNull() ?: 0)
                }
                editing = null
            },
            allowDecimal = false,
            quickSteps = listOf(-2.0, -1.0, 1.0, 2.0),
        )

        null -> Unit
    }

    // -- per-set menu -------------------------------------------------------------------------
    setMenuFor?.let { (exerciseId, setId) ->
        val exercise = session.exercises.firstOrNull { it.id == exerciseId }
        val set = exercise?.sets?.firstOrNull { it.id == setId }
        if (exercise != null && set != null) {
            WinterSheet(onDismiss = { setMenuFor = null }) {
                SheetTitle("Set options", data.exerciseName(exercise.exerciseId))
                SheetAction(
                    icon = Icons.Filled.WbSunny,
                    title = if (set.warmup) "Make it a working set" else "Mark as warm-up",
                    subtitle = "Warm-ups are logged but never counted toward volume or records",
                ) {
                    onUpdate { Actions.updateSet(it, exerciseId, setId, warmup = !set.warmup) }
                    setMenuFor = null
                }
                SheetAction(
                    icon = Icons.Filled.Delete,
                    title = "Delete this set",
                    tint = W.Bad,
                ) {
                    onUpdate { Actions.removeSet(it, exerciseId, setId) }
                    setMenuFor = null
                }
            }
        } else {
            setMenuFor = null
        }
    }

    // -- exercise options ---------------------------------------------------------------------
    optionsFor?.let { exerciseId ->
        val exercise = session.exercises.firstOrNull { it.id == exerciseId }
        if (exercise == null) {
            optionsFor = null
        } else {
            WinterSheet(onDismiss = { optionsFor = null }) {
                SheetTitle(data.exerciseName(exercise.exerciseId), "Change this movement for today only")
                SheetAction(
                    Icons.Filled.SwapHoriz,
                    "Swap for another movement",
                    "Keeps the sets, reps and position. Nothing already logged is lost.",
                ) {
                    optionsFor = null
                    replaceFor = exerciseId
                }
                SheetAction(
                    Icons.Filled.Tune,
                    "Adjust sets, reps and rest",
                    "Changes today's session only — the programme is untouched.",
                ) {
                    optionsFor = null
                    targetFor = exerciseId
                }
                SheetAction(
                    Icons.Filled.SkipNext,
                    if (exercise.skipped) "Unskip this movement" else "Skip this movement",
                    "Recorded as skipped, so the plan and the record still disagree honestly.",
                ) {
                    onUpdate { Actions.setSkipped(it, exerciseId, !exercise.skipped) }
                    optionsFor = null
                }
                SheetAction(Icons.Filled.Delete, "Remove from this session", tint = W.Bad) {
                    onUpdate { Actions.removeExerciseFromSession(it, exerciseId) }
                    optionsFor = null
                }
            }
        }
    }

    // -- swap / add ----------------------------------------------------------------------------
    replaceFor?.let { exerciseId ->
        ExercisePickerSheet(
            data = data,
            title = "Swap movement",
            onDismiss = { replaceFor = null },
            onPick = { picked ->
                onUpdate { Actions.replaceExercise(it, exerciseId, picked) }
                replaceFor = null
            },
            onCreateNew = {
                replaceFor = null
                onOpenLibrary()
            },
        )
    }

    if (addingExercise) {
        ExercisePickerSheet(
            data = data,
            title = "Add to this session",
            onDismiss = { addingExercise = false },
            onPick = { picked ->
                onUpdate { Actions.addExerciseToSession(it, picked) }
                addingExercise = false
            },
            onCreateNew = {
                addingExercise = false
                onOpenLibrary()
            },
        )
    }

    targetFor?.let { exerciseId ->
        val exercise = session.exercises.firstOrNull { it.id == exerciseId }
        if (exercise == null) {
            targetFor = null
        } else {
            TargetSheet(
                exercise = exercise,
                name = data.exerciseName(exercise.exerciseId),
                onDismiss = { targetFor = null },
                onApply = { sets, low, high, rest ->
                    onUpdate {
                        Actions.updateTarget(it, exerciseId, sets, low, high, rest)
                    }
                    targetFor = null
                },
            )
        }
    }

    if (confirmFinish) {
        ConfirmDialog(
            title = "Finish this session?",
            message = "Sets you never ticked are dropped rather than saved as zeroes. " +
                "Everything you logged is kept exactly as performed.",
            confirmLabel = "Finish",
            onConfirm = {
                val id = session.id
                onUpdate { Actions.finishSession(it, System.currentTimeMillis()) }
                restTimer.dismiss()
                confirmFinish = false
                finishedId = id
            },
            onDismiss = { confirmFinish = false },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Leave without finishing?",
            message = "The session stays in progress and you can pick it up where you left off. " +
                "Nothing is lost.",
            confirmLabel = "Leave it running",
            extraAction = "Discard the session" to {
                onUpdate { Actions.discardSession(it) }
                restTimer.dismiss()
                confirmDiscard = false
                onClose()
            },
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun ExerciseCard(
    data: AppData,
    exercise: SessionExercise,
    supersetWithNext: Boolean,
    supersetWithPrevious: Boolean,
    onEditWeight: (SetEntry) -> Unit,
    onEditReps: (SetEntry) -> Unit,
    onToggleDone: (SetEntry) -> Unit,
    onSetMenu: (SetEntry) -> Unit,
    onAddSet: () -> Unit,
    onOptions: () -> Unit,
    onStartRest: () -> Unit,
) {
    val catalogue = data.exercise(exercise.exerciseId)
    val complete = exercise.isComplete
    val last = remember(data.sessions, exercise.exerciseId) {
        Actions.lastPerformance(data, exercise.exerciseId)
    }
    val accentColor = when (exercise.priority) {
        Priority.ARMS -> W.Gold
        Priority.BACK -> W.Cyan
        Priority.NONE -> W.Muted
    }

    Column {
        ArcCard(
            padding = PaddingValues(16.dp),
            borderColor = if (complete) W.Good.copy(alpha = 0.30f) else W.Line,
            brush = if (exercise.skipped) {
                Brush.verticalGradient(listOf(W.Night, W.Void))
            } else {
                Grad.card
            },
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (exercise.priority == Priority.NONE) W.Void.copy(alpha = 0.6f) else W.GoldFilm)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        exercise.group.ifBlank { "—" },
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (exercise.priority != Priority.NONE) {
                            Text(
                                exercise.priority.symbol,
                                style = MaterialTheme.typography.bodySmall,
                                color = accentColor,
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            catalogue?.name ?: "Unknown movement",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (exercise.skipped) W.Faint else W.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    val detail = catalogue?.detail.orEmpty()
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = W.Faint,
                        )
                    }
                }
                Icon(
                    Icons.Filled.MoreVert,
                    "Options",
                    tint = W.Faint,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOptions)
                        .padding(7.dp),
                )
            }

            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("${exercise.plannedSets} × ${exercise.repRange}", color = W.Muted)
                if (exercise.rir.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Pill("RIR ${exercise.rir}", color = W.Muted)
                }
                if (exercise.style.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Pill(exercise.style, color = accentColor)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onStartRest)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Timer, "Start rest", tint = W.Faint, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        Fmt.clock(exercise.restSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Faint,
                    )
                }
            }

            if (exercise.skipped) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Skipped for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Warn,
                )
            } else {
                if (last != null) {
                    Spacer(Modifier.height(10.dp))
                    val best = last.workingSets.maxByOrNull { it.weightKg }
                    if (best != null) {
                        Text(
                            "Last time · ${Fmt.weightWithUnit(best.weightKg, data.prefs.unit)} × ${best.reps}" +
                                "  (${last.workingSets.size} sets)",
                            style = MaterialTheme.typography.labelSmall,
                            color = W.Ghost,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HairLine()
                Spacer(Modifier.height(6.dp))

                exercise.sets.forEachIndexed { index, set ->
                    SetRow(
                        index = index + 1,
                        set = set,
                        unit = data.prefs.unit.suffix,
                        displayWeight = Fmt.weight(set.weightKg, data.prefs.unit),
                        onWeight = { onEditWeight(set) },
                        onReps = { onEditReps(set) },
                        onToggle = { onToggleDone(set) },
                        onMenu = { onSetMenu(set) },
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(onClick = onAddSet)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, null, tint = W.Gold, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (exercise.sets.size >= exercise.plannedSets) "Add an extra set" else "Add a set",
                        style = MaterialTheme.typography.titleSmall,
                        color = W.Gold,
                    )
                    if (exercise.extraSets > 0) {
                        Spacer(Modifier.width(9.dp))
                        Pill("+${exercise.extraSets} extra", color = W.Good)
                    }
                }

                if (exercise.cue.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(W.Void.copy(alpha = 0.4f))
                            .padding(11.dp),
                    ) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(if (exercise.cue.length > 60) 32.dp else 16.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.6f)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            exercise.cue,
                            style = MaterialTheme.typography.bodySmall,
                            color = W.Muted,
                        )
                    }
                }
            }
        }

        // Supersets are drawn as a physical link between the two cards, which is faster to read
        // than a shared letter and matches how the plan was written.
        if (supersetWithNext) {
            Row(
                Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(2.dp).height(16.dp).background(W.GoldEdge))
                Spacer(Modifier.width(9.dp))
                Text(
                    "SUPERSET — straight into the next movement",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }
        } else if (supersetWithPrevious) {
            Spacer(Modifier.height(2.dp))
        }
    }
}

/**
 * One set.
 *
 * The two numbers are the interface. Both are large tap targets that open a full keypad, and the
 * tick is a 46dp square, because this is pressed with chalk on the hands and no attention spare.
 */
@Composable
private fun SetRow(
    index: Int,
    set: SetEntry,
    unit: String,
    displayWeight: String,
    onWeight: () -> Unit,
    onReps: () -> Unit,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (set.warmup) W.Warn.copy(alpha = 0.16f) else Color.Transparent)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (set.warmup) "W" else index.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (set.warmup) W.Warn else W.Ghost,
            )
        }

        Spacer(Modifier.width(8.dp))
        NumberCell(
            value = displayWeight,
            suffix = unit,
            onClick = onWeight,
            modifier = Modifier.weight(1f),
            done = set.done,
        )
        Text(
            "×",
            style = MaterialTheme.typography.titleMedium,
            color = W.Ghost,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        NumberCell(
            value = set.reps.toString(),
            suffix = "reps",
            onClick = onReps,
            modifier = Modifier.weight(1f),
            done = set.done,
        )

        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (set.done) W.Good.copy(alpha = 0.22f) else W.Void.copy(alpha = 0.5f))
                .border(
                    1.dp,
                    if (set.done) W.Good.copy(alpha = 0.6f) else W.Line,
                    RoundedCornerShape(13.dp),
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                if (set.done) "Completed" else "Mark complete",
                tint = if (set.done) W.Good else W.Ghost,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun NumberCell(
    value: String,
    suffix: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    done: Boolean = false,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(W.Void.copy(alpha = 0.5f))
            .border(1.dp, if (done) W.Good.copy(alpha = 0.25f) else W.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (done) W.Ink else W.Ink.copy(alpha = 0.85f),
            maxLines = 1,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            suffix,
            style = MaterialTheme.typography.labelSmall,
            color = W.Ghost,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun TargetSheet(
    exercise: SessionExercise,
    name: String,
    onDismiss: () -> Unit,
    onApply: (Int, Int, Int, Int) -> Unit,
) {
    var sets by remember { mutableIntStateOf(exercise.plannedSets) }
    var low by remember { mutableIntStateOf(exercise.repLow) }
    var high by remember { mutableIntStateOf(exercise.repHigh) }
    var rest by remember { mutableIntStateOf(exercise.restSeconds) }

    WinterSheet(onDismiss = onDismiss) {
        SheetTitle("Adjust target", name)
        StepperRow("Sets", sets.toString()) { sets = (sets + it).coerceIn(1, 30) }
        StepperRow("Lowest reps", low.toString()) { low = (low + it).coerceIn(1, 100) }
        StepperRow("Highest reps", high.toString()) { high = (high + it).coerceIn(low, 100) }
        StepperRow("Rest", Fmt.clock(rest), step = 15) { rest = (rest + it).coerceIn(0, 900) }
        Spacer(Modifier.height(18.dp))
        Text(
            "This changes today only. The programme keeps its own prescription.",
            style = MaterialTheme.typography.labelSmall,
            color = W.Ghost,
        )
        Spacer(Modifier.height(14.dp))
        GoldButton("Apply", { onApply(sets, low, high, rest) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StepperRow(label: String, value: String, step: Int = 1, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = W.Ink, modifier = Modifier.weight(1f))
        StepButton("−") { onChange(-step) }
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = W.GoldBright,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(84.dp),
        )
        StepButton("+") { onChange(step) }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(W.Void.copy(alpha = 0.55f))
            .border(1.dp, W.Line, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineSmall, color = W.Gold)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extraAction: Pair<String, () -> Unit>? = null,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Grad.cardLit)
                .border(1.dp, W.Line, RoundedCornerShape(24.dp))
                .padding(22.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = W.Ink)
            Spacer(Modifier.height(9.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = W.Muted)
            Spacer(Modifier.height(20.dp))
            GoldButton(confirmLabel, onConfirm, Modifier.fillMaxWidth())
            if (extraAction != null) {
                Spacer(Modifier.height(9.dp))
                GhostButton(extraAction.first, extraAction.second, Modifier.fillMaxWidth(), color = W.Bad)
            }
            Spacer(Modifier.height(9.dp))
            GhostButton("Cancel", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
