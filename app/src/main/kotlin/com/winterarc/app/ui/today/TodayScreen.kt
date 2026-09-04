package com.winterarc.app.ui.today

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.GoldButton
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.Metric
import com.winterarc.app.ui.kit.ProgressRing
import com.winterarc.app.ui.kit.RoundIcon
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Actions
import com.winterarc.core.Analytics
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import com.winterarc.core.WeekDays
import com.winterarc.core.Priority
import com.winterarc.core.Routine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val headerDate = DateTimeFormatter.ofPattern("EEEE d MMMM")

/**
 * The landing screen.
 *
 * It answers two questions and nothing else: what am I training now, and am I moving forward.
 * Anything that answers neither lives on another tab.
 */
@Composable
fun TodayScreen(
    data: AppData,
    onUpdate: ((AppData) -> AppData) -> Unit,
    onTrain: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val routine = data.routineFor(today.dayOfWeek)
    val active = data.activeSession
    val consistency = remember(data.sessions) { Analytics.consistency(data.sessions, today) }
    val doneToday = remember(data.sessions) {
        data.sessions.any { it.finished && it.date == today }
    }
    var showPicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "WINTER ARC",
                    style = MaterialTheme.typography.headlineMedium,
                    color = W.Gold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    today.format(headerDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = W.Faint,
                )
            }
            RoundIcon(Icons.Filled.CalendarMonth, "Programme", onOpenPlan)
            Spacer(Modifier.width(9.dp))
            RoundIcon(Icons.Filled.Settings, "Settings", onOpenSettings)
        }

        Spacer(Modifier.height(18.dp))

        when {
            active != null -> ResumeCard(
                title = active.title,
                completion = active.completion,
                accent = W.accent(active.accent),
                doneSets = active.exercises.sumOf { it.completedCount },
                totalSets = active.exercises.sumOf { it.plannedSets },
                onResume = onTrain,
            )

            routine != null -> TodayCard(
                routine = routine,
                accent = W.accent(routine.accent),
                alreadyDone = doneToday,
                onStart = {
                    onUpdate { Actions.startSession(it, routine.id, today, System.currentTimeMillis()) }
                    onTrain()
                },
                onSomethingElse = { showPicker = true },
            )

            else -> RestCard(
                trainedThisWeek = consistency.sessionsThisWeek,
                onTrainAnyway = { showPicker = true },
            )
        }

        Spacer(Modifier.height(20.dp))
        WeekStrip(data = data, today = today)

        Spacer(Modifier.height(20.dp))
        SectionHeader("This week")
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                Metric(
                    label = "Streak",
                    value = consistency.weekStreak.toString(),
                    unit = if (consistency.weekStreak == 1) "wk" else "wks",
                    caption = "best ${consistency.longestWeekStreak}",
                    valueColor = W.GoldBright,
                )
            }
            ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                Metric(
                    label = "Sessions",
                    value = consistency.sessionsThisWeek.toString(),
                    caption = "of ${data.goals.weeklySessionTarget} planned",
                )
            }
            ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                val weekVolume = remember(data.sessions) {
                    val start = Analytics.weekStart(today)
                    data.sessions.filter { it.finished && !it.date.isBefore(start) }.sumOf { it.volumeKg }
                }
                Metric(
                    label = "Volume",
                    value = Fmt.volume(weekVolume, data.prefs.unit),
                    unit = data.prefs.unit.suffix,
                )
            }
        }

        val priorityGoal = data.goals.liftGoals.firstOrNull()
        if (priorityGoal != null) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("The priority")
            PriorityCard(data = data, today = today)
        }

        val last = remember(data.sessions) { data.history.firstOrNull() }
        if (last != null) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Last session")
            ArcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(last.title, style = MaterialTheme.typography.titleLarge, color = W.Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${last.date.format(DateTimeFormatter.ofPattern("d MMM"))} · " +
                                "${last.workingSetCount} sets · " +
                                "${Fmt.volume(last.volumeKg, data.prefs.unit)} ${data.prefs.unit.suffix}",
                            style = MaterialTheme.typography.bodySmall,
                            color = W.Faint,
                        )
                    }
                    last.durationMinutes?.let {
                        Text(
                            Fmt.duration(it),
                            style = MaterialTheme.typography.titleMedium,
                            color = W.Muted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            GhostButton("Exercises", onOpenLibrary, Modifier.weight(1f), icon = Icons.Filled.Add)
            GhostButton("Programme", onOpenPlan, Modifier.weight(1f), icon = Icons.Filled.CalendarMonth)
        }
    }

    if (showPicker) {
        RoutinePickerDialog(
            data = data,
            onDismiss = { showPicker = false },
            onPick = { routineId ->
                showPicker = false
                onUpdate { Actions.startSession(it, routineId, today, System.currentTimeMillis()) }
                onTrain()
            },
            onOpenSession = {
                showPicker = false
                onUpdate {
                    Actions.startSession(it, null, today, System.currentTimeMillis(), "Open session")
                }
                onTrain()
            },
        )
    }
}

/** The hero: what today is, and one obvious way to begin it. */
@Composable
private fun TodayCard(
    routine: Routine,
    accent: Color,
    alreadyDone: Boolean,
    onStart: () -> Unit,
    onSomethingElse: () -> Unit,
) {
    val priority = routine.items.firstOrNull { it.priority != Priority.NONE }?.priority
    ArcCard(
        brush = Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.15f), W.NightHi, W.Night),
        ),
        borderColor = accent.copy(alpha = 0.30f),
        padding = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Label("Today", color = accent)
            if (priority != null && priority != Priority.NONE) {
                Spacer(Modifier.width(9.dp))
                Text(
                    "${priority.symbol} ${priority.display.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Gold,
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(
            routine.name,
            style = MaterialTheme.typography.headlineLarge,
            color = W.Ink,
        )
        if (routine.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(routine.subtitle, style = MaterialTheme.typography.bodyMedium, color = W.Muted)
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatDot("${routine.items.size}", "movements")
            Spacer(Modifier.width(20.dp))
            StatDot("${routine.totalSets}", "sets")
            Spacer(Modifier.width(20.dp))
            StatDot("~${routine.estimatedMinutes}", "minutes")
        }

        if (routine.note.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(W.Void.copy(alpha = 0.45f))
                    .padding(12.dp),
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(W.Warn),
                )
                Spacer(Modifier.width(11.dp))
                Text(
                    routine.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Muted,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        GoldButton(
            text = if (alreadyDone) "TRAIN AGAIN" else "START ${routine.name.uppercase()}",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.PlayArrow,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "Train something else",
            style = MaterialTheme.typography.titleSmall,
            color = W.Faint,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSomethingElse)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ResumeCard(
    title: String,
    completion: Double,
    accent: Color,
    doneSets: Int,
    totalSets: Int,
    onResume: () -> Unit,
) {
    ArcCard(
        brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), W.NightHi, W.Night)),
        borderColor = accent.copy(alpha = 0.35f),
        padding = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = completion.toFloat(),
                modifier = Modifier.size(84.dp),
                color = accent,
                strokeWidth = 7.dp,
            ) {
                Text(
                    "${(completion * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = W.Ink,
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Label("In progress", color = accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = W.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$doneSets of $totalSets sets logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Faint,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        GoldButton("RESUME SESSION", onResume, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
    }
}

@Composable
private fun RestCard(trainedThisWeek: Int, onTrainAnyway: () -> Unit) {
    ArcCard(brush = Grad.blueCard, borderColor = W.LineBlue, padding = PaddingValues(20.dp)) {
        Label("Today", color = W.Cyan)
        Spacer(Modifier.height(10.dp))
        Text("Rest", style = MaterialTheme.typography.headlineLarge, color = W.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Prescribed, not earned. Seven sessions a week leaves no room to skip it — " +
                "which is why streaks here count weeks, never days.",
            style = MaterialTheme.typography.bodyMedium,
            color = W.Muted,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "$trainedThisWeek ${if (trainedThisWeek == 1) "session" else "sessions"} logged this week",
            style = MaterialTheme.typography.titleSmall,
            color = W.Cyan,
        )
        Spacer(Modifier.height(16.dp))
        GhostButton("Train anyway", onTrainAnyway, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatDot(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = W.Ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = W.Faint)
    }
}

/**
 * The week at a glance.
 *
 * A filled dot is a session actually completed; a hollow ring is one scheduled but not yet done.
 * The distinction is the whole point -- a plan is not an achievement.
 */
@Composable
private fun WeekStrip(data: AppData, today: LocalDate) {
    val weekStart = remember(today) { Analytics.weekStart(today) }
    val trained = remember(data.sessions, weekStart) {
        data.sessions.filter { it.finished && !it.date.isBefore(weekStart) && it.date.isBefore(weekStart.plusDays(7)) }
            .map { it.date }
            .toSet()
    }

    ArcCard(padding = PaddingValues(vertical = 15.dp, horizontal = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            WeekDays.forEach { day ->
                val date = weekStart.plusDays((day.value - 1).toLong())
                val isToday = date == today
                val done = date in trained
                val scheduled = data.routineFor(day) != null
                val accent = data.routineFor(day)?.let { W.accent(it.accent) } ?: W.Ghost

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Fmt.shortDay(day).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) W.GoldBright else W.Faint,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .size(if (isToday) 15.dp else 12.dp)
                            .clip(CircleShape)
                            .background(if (done) accent else Color.Transparent)
                            .border(
                                width = if (done) 0.dp else 1.5.dp,
                                color = when {
                                    done -> Color.Transparent
                                    scheduled -> accent.copy(alpha = 0.55f)
                                    else -> W.Ghost
                                },
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(width = 12.dp, height = 2.dp)
                            .clip(CircleShape)
                            .background(if (isToday) W.Gold else Color.Transparent),
                    )
                }
            }
        }
    }
}

/** Progress on the movement the whole programme is built around. */
@Composable
private fun PriorityCard(data: AppData, today: LocalDate) {
    val goal = data.goals.liftGoals.first()
    val progress = remember(data) { Analytics.goalProgress(data) }
        .firstOrNull { it.label == goal.label || it.label == data.exerciseName(goal.exerciseId) }

    ArcCard(
        brush = Brush.verticalGradient(listOf(W.GoldFilm, W.NightHi, W.Night)),
        borderColor = W.GoldEdge,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = (progress?.fraction ?: 0.0).toFloat(),
                modifier = Modifier.size(92.dp),
                color = W.Gold,
                strokeWidth = 8.dp,
                milestone = progress?.milestoneFraction?.toFloat(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Fmt.weight(progress?.current ?: goal.startKg, data.prefs.unit),
                        style = MaterialTheme.typography.headlineSmall,
                        color = W.GoldBright,
                    )
                    Text(
                        data.prefs.unit.suffix,
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Faint,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    goal.label.ifBlank { data.exerciseName(goal.exerciseId) },
                    style = MaterialTheme.typography.titleLarge,
                    color = W.Ink,
                )
                Spacer(Modifier.height(8.dp))
                GoalLine("Milestone", Fmt.weightWithUnit(goal.milestoneKg, data.prefs.unit), W.Muted)
                Spacer(Modifier.height(3.dp))
                GoalLine("End goal", Fmt.weightWithUnit(goal.targetKg, data.prefs.unit), W.Gold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The notch on the ring is the 12-week checkpoint.",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }
        }
    }
}

@Composable
private fun GoalLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = W.Faint)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}
