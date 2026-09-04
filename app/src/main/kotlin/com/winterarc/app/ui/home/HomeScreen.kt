package com.winterarc.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.components.*
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.analytics.PersonalRecord
import com.winterarc.domain.model.DayType
import com.winterarc.domain.model.WorkoutTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val todayTemplate: WorkoutTemplate? = null,
    val exerciseNames: Map<String, String> = emptyMap(),
    val hasSessionInProgress: Boolean = false,
    val weekStreak: Int = 0,
    val sessionsThisWeek: Int = 0,
    val totalWorkouts: Int = 0,
    val latestPr: PersonalRecord? = null,
    val latestPrExercise: String? = null,
    val currentWeightKg: Double? = null,
    val weightChangeKg: Double? = null,
    val allTemplates: List<WorkoutTemplate> = emptyList(),
    val loading: Boolean = true,
)

private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

/**
 * The landing screen: what to do today, and whether progress is being made.
 *
 * Everything above the fold answers one of two questions — "what am I training?" and
 * "am I moving forward?". Anything that answers neither belongs on another screen.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onTrainSomethingElse: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenBody: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(containerColor = WinterArcColors.NightDeep) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "WINTER ARC",
                        style = MaterialTheme.typography.headlineMedium,
                        color = WinterArcColors.Gold,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        state.today.format(dateFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WinterArcColors.Muted,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.Settings,
                        "Settings",
                        tint = WinterArcColors.Muted,
                    )
                }
            }

            TodayCard(
                template = state.todayTemplate,
                exerciseNames = state.exerciseNames,
                hasSessionInProgress = state.hasSessionInProgress,
                onStart = onStartWorkout,
                onResume = onResumeWorkout,
                onTrainElse = onTrainSomethingElse,
            )

            SectionLabel("Progress")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        label = "Week streak",
                        value = state.weekStreak.toString(),
                        caption = if (state.weekStreak == 1) "week" else "weeks",
                    )
                }
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        label = "This week",
                        value = state.sessionsThisWeek.toString(),
                        caption = "sessions",
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        label = "Total workouts",
                        value = state.totalWorkouts.toString(),
                        caption = "logged",
                    )
                }
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        label = "Weight",
                        value = state.currentWeightKg?.let { "${(it * 10).roundToInt() / 10.0}" } ?: "—",
                        caption = state.weightChangeKg?.let { c ->
                            val sign = if (c > 0) "+" else ""
                            "$sign${(c * 10).roundToInt() / 10.0} kg since start"
                        } ?: "kg",
                    )
                }
            }

            if (state.latestPr != null) {
                WinterCard(accent = true) {
                    Text(
                        "LATEST RECORD",
                        style = MaterialTheme.typography.labelSmall,
                        color = WinterArcColors.Muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.latestPrExercise ?: "Exercise",
                        style = MaterialTheme.typography.titleLarge,
                        color = WinterArcColors.White,
                    )
                    Text(
                        buildString {
                            append(state.latestPr.type.label)
                            append(" · ")
                            when (state.latestPr.type) {
                                com.winterarc.domain.analytics.PrType.REPS ->
                                    append("${state.latestPr.value.roundToInt()} reps at ${state.latestPr.weightKg} kg")
                                com.winterarc.domain.analytics.PrType.SESSION_VOLUME ->
                                    append("${state.latestPr.value.roundToInt()} kg total")
                                else ->
                                    append("${(state.latestPr.value * 10).roundToInt() / 10.0} kg")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = WinterArcColors.Gold,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton("History", onOpenHistory, Modifier.weight(1f))
                OutlineButton("Dashboard", onOpenDashboard, Modifier.weight(1f))
            }
            OutlineButton("Body & measurements", onOpenBody, Modifier.fillMaxWidth())

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TodayCard(
    template: WorkoutTemplate?,
    exerciseNames: Map<String, String>,
    hasSessionInProgress: Boolean,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onTrainElse: () -> Unit,
) {
    WinterCard(accent = true) {
        Text("TODAY", style = MaterialTheme.typography.labelSmall, color = WinterArcColors.Muted)
        Spacer(Modifier.height(6.dp))

        when {
            template == null -> {
                Text(
                    "Nothing scheduled",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WinterArcColors.White,
                )
                Text(
                    "No template is assigned to today. You can still train whatever you want.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WinterArcColors.Muted,
                )
            }
            template.dayType == DayType.REST -> {
                Text(
                    "Rest day",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WinterArcColors.NightBlueBright,
                )
                Text(
                    template.notes ?: "Recovery is part of the programme.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WinterArcColors.Muted,
                )
            }
            template.dayType == DayType.MMA -> {
                Text(
                    template.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = WinterArcColors.NightBlueBright,
                )
                Text(
                    template.notes ?: "No lifting scheduled today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WinterArcColors.Muted,
                )
            }
            else -> {
                Text(
                    template.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = WinterArcColors.White,
                )
                Text(
                    "${template.exercises.size} exercises  ·  ${template.plannedSetCount} planned sets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WinterArcColors.Gold,
                )
                if (template.exercises.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    template.exercises.sortedBy { it.position }.take(5).forEach { pe ->
                        Text(
                            "· ${exerciseNames[pe.exerciseId] ?: "Exercise"}  " +
                                "${pe.sets}×${pe.repRangeLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WinterArcColors.Muted,
                        )
                    }
                    if (template.exercises.size > 5) {
                        Text(
                            "+ ${template.exercises.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = WinterArcColors.Faint,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (hasSessionInProgress) {
            GoldButton("RESUME WORKOUT", onResume, Modifier.fillMaxWidth())
        } else if (template != null && template.dayType == DayType.TRAINING && template.exercises.isNotEmpty()) {
            GoldButton("START WORKOUT", onStart, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))
        OutlineButton("Train something else", onTrainElse, Modifier.fillMaxWidth())
    }
}
