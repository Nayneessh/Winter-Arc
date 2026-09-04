package com.winterarc.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winterarc.app.Repository
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.GoldButton
import com.winterarc.app.ui.kit.HairLine
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.OverlayScreen
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.kit.SegmentedControl
import com.winterarc.app.ui.kit.SettingRow
import com.winterarc.app.ui.kit.SheetTitle
import com.winterarc.app.ui.kit.WinterField
import com.winterarc.app.ui.kit.WinterSheet
import com.winterarc.app.ui.kit.WinterSwitch
import com.winterarc.app.ui.plan.ExercisePickerSheet
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Actions
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import com.winterarc.core.Goals
import com.winterarc.core.LiftGoal
import com.winterarc.core.WeightUnit
import com.winterarc.core.newId

/**
 * Settings, targets and the data itself.
 *
 * Export is deliberately prominent. Everything lives in one file on this device and nowhere
 * else, which is a feature -- but only if getting a copy out is trivial.
 */
@Composable
fun SettingsScreen(
    data: AppData,
    repository: Repository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var editingGoals by remember { mutableStateOf(false) }
    var editingLift by remember { mutableStateOf<LiftGoal?>(null) }
    var addingLift by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(repository.exportJson().toByteArray())
                }
            }
            status = "Backup written."
        }
    }

    val exportSets = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(repository.exportSetsCsv().toByteArray())
                }
            }
            status = "Every set exported."
        }
    }

    val exportBody = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(repository.exportBodyCsv().toByteArray())
                }
            }
            status = "Body data exported."
        }
    }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            status = if (text != null && repository.importJson(text)) {
                "Restored from backup."
            } else {
                "That file could not be read as a Winter Arc backup."
            }
        }
    }

    OverlayScreen(title = "Settings", onBack = onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            SectionHeader("Units and behaviour")
            ArcCard {
                Label("Weight unit")
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    options = WeightUnit.entries.map { it.display },
                    selectedIndex = WeightUnit.entries.indexOf(data.prefs.unit),
                    onSelect = { index ->
                        repository.update {
                            Actions.updatePrefs(it, it.prefs.copy(unit = WeightUnit.entries[index]))
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Everything is stored in kilograms whatever you choose here, so switching " +
                        "never rewrites a single recorded set.",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )

                Spacer(Modifier.height(16.dp))
                HairLine()
                SettingRow(
                    title = "Weight step",
                    subtitle = "The increment offered on the keypad",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(1.0, 2.5, 5.0).forEach { step ->
                                val on = data.prefs.weightStepKg == step
                                Text(
                                    Fmt.trim(step),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (on) W.Void else W.Muted,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (on) W.Gold else W.Void.copy(alpha = 0.5f))
                                        .clickable {
                                            repository.update {
                                                Actions.updatePrefs(it, it.prefs.copy(weightStepKg = step))
                                            }
                                        }
                                        .padding(horizontal = 11.dp, vertical = 7.dp),
                                )
                            }
                        }
                    },
                )
                HairLine()
                SettingRow(
                    title = "Start rest automatically",
                    subtitle = "Begins the countdown the moment a set is ticked",
                    trailing = {
                        WinterSwitch(data.prefs.restTimerAutoStart) { on ->
                            repository.update {
                                Actions.updatePrefs(it, it.prefs.copy(restTimerAutoStart = on))
                            }
                        }
                    },
                )
                HairLine()
                SettingRow(
                    title = "Keep the screen awake",
                    subtitle = "Only while a workout is actually in progress",
                    trailing = {
                        WinterSwitch(data.prefs.keepScreenOn) { on ->
                            repository.update {
                                Actions.updatePrefs(it, it.prefs.copy(keepScreenOn = on))
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Targets")
            ArcCard(onClick = { editingGoals = true }) {
                GoalLine(
                    "Bodyweight",
                    data.goals.startWeightKg,
                    data.goals.targetWeightKg,
                    data.prefs.unit.suffix,
                    data.prefs.unit,
                )
                Spacer(Modifier.height(10.dp))
                GoalLine(
                    "Body fat",
                    data.goals.startBodyFatPct,
                    data.goals.targetBodyFatPct,
                    "%",
                    null,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Sessions per week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = W.Muted,
                    )
                    Text(
                        data.goals.weeklySessionTarget.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = W.Ink,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Tap to edit", style = MaterialTheme.typography.labelSmall, color = W.Ghost)
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Lift goals")
            data.goals.liftGoals.forEach { goal ->
                ArcCard(
                    Modifier.padding(bottom = 9.dp),
                    onClick = { editingLift = goal },
                    padding = PaddingValues(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                goal.label.ifBlank { data.exerciseName(goal.exerciseId) },
                                style = MaterialTheme.typography.titleMedium,
                                color = W.Ink,
                            )
                            Text(
                                data.exerciseName(goal.exerciseId),
                                style = MaterialTheme.typography.labelSmall,
                                color = W.Ghost,
                            )
                        }
                        Text(
                            "${Fmt.weight(goal.startKg, data.prefs.unit)} → " +
                                "${Fmt.weight(goal.milestoneKg, data.prefs.unit)} → " +
                                Fmt.weightWithUnit(goal.targetKg, data.prefs.unit),
                            style = MaterialTheme.typography.titleSmall,
                            color = W.GoldBright,
                        )
                    }
                }
            }
            GhostButton(
                "Add a lift goal",
                { addingLift = true },
                Modifier.fillMaxWidth(),
                icon = Icons.Filled.Add,
            )

            Spacer(Modifier.height(22.dp))
            SectionHeader("Your data")
            ArcCard {
                Text(
                    "Everything lives in one file on this phone. No account, no server, and it " +
                        "works with the aeroplane mode on forever.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = W.Muted,
                )
                Spacer(Modifier.height(16.dp))
                GhostButton(
                    "Back up everything (JSON)",
                    { exportJson.launch("winter-arc-backup.json") },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Download,
                )
                Spacer(Modifier.height(9.dp))
                GhostButton(
                    "Export every set (CSV)",
                    { exportSets.launch("winter-arc-sets.csv") },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Download,
                )
                Spacer(Modifier.height(9.dp))
                GhostButton(
                    "Export body data (CSV)",
                    { exportBody.launch("winter-arc-body.csv") },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Download,
                )
                Spacer(Modifier.height(9.dp))
                GhostButton(
                    "Restore from a backup",
                    { importJson.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Upload,
                )
                if (status != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(status!!, style = MaterialTheme.typography.bodySmall, color = W.Good)
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Reset")
            ArcCard {
                Text(
                    "Restores the seeded Winter Arc programme and erases every session, " +
                        "check-in and movement you added. Back up first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Faint,
                )
                Spacer(Modifier.height(14.dp))
                GhostButton(
                    "Erase everything and start over",
                    { confirmReset = true },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.DeleteOutline,
                    color = W.Bad,
                )
            }

            Spacer(Modifier.height(26.dp))
            Text(
                "Winter Arc · built for one training block, and every one after it.",
                style = MaterialTheme.typography.labelSmall,
                color = W.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (editingGoals) {
        GoalsSheet(
            goals = data.goals,
            unit = data.prefs.unit,
            onDismiss = { editingGoals = false },
            onSave = { updated ->
                repository.update { Actions.updateGoals(it, updated) }
                editingGoals = false
            },
        )
    }

    if (addingLift) {
        ExercisePickerSheet(
            data = data,
            title = "Which lift?",
            onDismiss = { addingLift = false },
            onPick = { exerciseId ->
                addingLift = false
                editingLift = LiftGoal(
                    id = newId(),
                    exerciseId = exerciseId,
                    label = data.exerciseName(exerciseId),
                    startKg = 0.0,
                    milestoneKg = 0.0,
                    targetKg = 0.0,
                )
            },
        )
    }

    editingLift?.let { goal ->
        LiftGoalSheet(
            goal = goal,
            name = data.exerciseName(goal.exerciseId),
            unit = data.prefs.unit,
            onDismiss = { editingLift = null },
            onSave = { updated ->
                repository.update { current ->
                    val existing = current.goals.liftGoals.any { it.id == updated.id }
                    Actions.updateGoals(
                        current,
                        current.goals.copy(
                            liftGoals = if (existing) {
                                current.goals.liftGoals.map { if (it.id == updated.id) updated else it }
                            } else {
                                current.goals.liftGoals + updated
                            },
                        ),
                    )
                }
                editingLift = null
            },
            onDelete = {
                repository.update { current ->
                    Actions.updateGoals(
                        current,
                        current.goals.copy(
                            liftGoals = current.goals.liftGoals.filterNot { it.id == goal.id },
                        ),
                    )
                }
                editingLift = null
            },
        )
    }

    if (confirmReset) {
        WinterSheet(onDismiss = { confirmReset = false }) {
            SheetTitle(
                "Erase everything?",
                "Every session, check-in and movement you added is deleted and the seeded " +
                    "programme comes back. This cannot be undone.",
            )
            GhostButton(
                "Erase everything",
                {
                    repository.resetToSeed()
                    confirmReset = false
                    onBack()
                },
                Modifier.fillMaxWidth(),
                color = W.Bad,
            )
            Spacer(Modifier.height(9.dp))
            GoldButton("Keep my data", { confirmReset = false }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GoalLine(
    label: String,
    start: Double?,
    target: Double?,
    suffix: String,
    unit: WeightUnit?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = W.Muted)
        Text(
            if (start == null || target == null) {
                "Not set"
            } else {
                val s = unit?.let { Fmt.weight(start, it) } ?: Fmt.trim(start)
                val t = unit?.let { Fmt.weight(target, it) } ?: Fmt.trim(target)
                "$s → $t $suffix"
            },
            style = MaterialTheme.typography.titleSmall,
            color = W.Ink,
        )
    }
}

@Composable
private fun GoalsSheet(
    goals: Goals,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (Goals) -> Unit,
) {
    var startWeight by remember {
        mutableStateOf(goals.startWeightKg?.let { Fmt.weight(it, unit) } ?: "")
    }
    var targetWeight by remember {
        mutableStateOf(goals.targetWeightKg?.let { Fmt.weight(it, unit) } ?: "")
    }
    var startFat by remember { mutableStateOf(goals.startBodyFatPct?.let { Fmt.trim(it) } ?: "") }
    var targetFat by remember { mutableStateOf(goals.targetBodyFatPct?.let { Fmt.trim(it) } ?: "") }
    var perWeek by remember { mutableStateOf(goals.weeklySessionTarget.toString()) }

    WinterSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp)) {
            SheetTitle("Targets", "Progress is measured from the start value to the target.")

            Label("Bodyweight (${unit.suffix})")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                WinterField(startWeight, { startWeight = it }, "Start", Modifier.weight(1f), KeyboardType.Decimal)
                WinterField(targetWeight, { targetWeight = it }, "Target", Modifier.weight(1f), KeyboardType.Decimal)
            }

            Spacer(Modifier.height(18.dp))
            Label("Body fat (%)")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                WinterField(startFat, { startFat = it }, "Start", Modifier.weight(1f), KeyboardType.Decimal)
                WinterField(targetFat, { targetFat = it }, "Target", Modifier.weight(1f), KeyboardType.Decimal)
            }

            Spacer(Modifier.height(18.dp))
            WinterField(perWeek, { perWeek = it }, "Sessions per week", keyboardType = KeyboardType.Number)

            Spacer(Modifier.height(20.dp))
            GoldButton(
                "SAVE",
                {
                    onSave(
                        goals.copy(
                            startWeightKg = startWeight.toDoubleOrNull()
                                ?.let { Fmt.fromDisplayWeight(it, unit) },
                            targetWeightKg = targetWeight.toDoubleOrNull()
                                ?.let { Fmt.fromDisplayWeight(it, unit) },
                            startBodyFatPct = startFat.toDoubleOrNull(),
                            targetBodyFatPct = targetFat.toDoubleOrNull(),
                            weeklySessionTarget = perWeek.toIntOrNull()?.coerceIn(1, 14)
                                ?: goals.weeklySessionTarget,
                        ),
                    )
                },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LiftGoalSheet(
    goal: LiftGoal,
    name: String,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (LiftGoal) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember { mutableStateOf(goal.label) }
    var start by remember { mutableStateOf(if (goal.startKg > 0) Fmt.weight(goal.startKg, unit) else "") }
    var milestone by remember {
        mutableStateOf(if (goal.milestoneKg > 0) Fmt.weight(goal.milestoneKg, unit) else "")
    }
    var target by remember { mutableStateOf(if (goal.targetKg > 0) Fmt.weight(goal.targetKg, unit) else "") }

    WinterSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp)) {
            SheetTitle("Lift goal", name)
            WinterField(label, { label = it }, "Label")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WinterField(start, { start = it }, "Now", Modifier.weight(1f), KeyboardType.Decimal)
                WinterField(milestone, { milestone = it }, "Milestone", Modifier.weight(1f), KeyboardType.Decimal)
                WinterField(target, { target = it }, "Goal", Modifier.weight(1f), KeyboardType.Decimal)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "The milestone is drawn as a notch on the ring — the checkpoint on the way to " +
                    "the target.",
                style = MaterialTheme.typography.labelSmall,
                color = W.Ghost,
            )
            Spacer(Modifier.height(20.dp))
            GoldButton(
                "SAVE",
                {
                    onSave(
                        goal.copy(
                            label = label.trim(),
                            startKg = start.toDoubleOrNull()?.let { Fmt.fromDisplayWeight(it, unit) } ?: 0.0,
                            milestoneKg = milestone.toDoubleOrNull()?.let { Fmt.fromDisplayWeight(it, unit) } ?: 0.0,
                            targetKg = target.toDoubleOrNull()?.let { Fmt.fromDisplayWeight(it, unit) } ?: 0.0,
                        ),
                    )
                },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(9.dp))
            GhostButton(
                "Delete this goal",
                onDelete,
                Modifier.fillMaxWidth(),
                icon = Icons.Filled.DeleteOutline,
                color = W.Bad,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
