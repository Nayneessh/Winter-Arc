package com.winterarc.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.winterarc.app.AppContainer
import com.winterarc.app.ui.components.*
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.analytics.ExerciseHistory
import com.winterarc.domain.analytics.OneRepMax
import com.winterarc.domain.analytics.Progression
import com.winterarc.domain.analytics.ProgressionPoint
import com.winterarc.domain.model.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val fullDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val shortDate = DateTimeFormatter.ofPattern("d MMM")

data class HistoryUiState(
    val sessions: List<WorkoutSession> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val selected: WorkoutSession? = null,
    val exerciseHistory: ExerciseHistory? = null,
    val exerciseProgression: List<ProgressionPoint> = emptyList(),
    val exerciseName: String = "",
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val sessions = repo.completedHistory().sortedByDescending { it.date }
            val names = repo.allExercises().associate { it.id to it.name }
            _state.value = _state.value.copy(sessions = sessions, names = names, loading = false)
        }
    }

    fun open(sessionId: String) {
        _state.value = _state.value.copy(selected = _state.value.sessions.firstOrNull { it.id == sessionId })
    }

    fun closeSession() { _state.value = _state.value.copy(selected = null) }

    fun openExercise(exerciseId: String) {
        val s = _state.value
        _state.value = s.copy(
            exerciseHistory = ExerciseHistory.from(exerciseId, s.sessions),
            exerciseProgression = Progression.forExercise(exerciseId, s.sessions),
            exerciseName = s.names[exerciseId] ?: "Exercise",
        )
    }

    fun closeExercise() {
        _state.value = _state.value.copy(exerciseHistory = null, exerciseProgression = emptyList())
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(container) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = { Text("History") },
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
        if (state.sessions.isEmpty() && !state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    "No workouts yet",
                    "Finished workouts are kept here permanently, exactly as they were performed.",
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.sessions, key = { it.id }) { session ->
                WinterCard(onClick = { onOpen(session.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                session.date.format(fullDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = WinterArcColors.Muted,
                            )
                        }
                        session.durationSeconds?.let {
                            Text(
                                "${it / 60} min",
                                style = MaterialTheme.typography.labelMedium,
                                color = WinterArcColors.Gold,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("${session.performedExercises.size} exercises")
                        Chip("${session.actualSetTotal} sets")
                        if (session.extraSetTotal > 0) {
                            Chip("+${session.extraSetTotal} extra", color = WinterArcColors.Success)
                        }
                        Chip("${session.totalVolumeKg.roundToInt()} kg", color = WinterArcColors.Gold)
                    }
                }
            }
        }
    }
}

/**
 * A completed session, shown exactly as it was performed.
 *
 * The prescribed figures displayed here are the ones snapshotted when the session began, not
 * whatever the template says today. Changing the plan later never rewrites this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    session: WorkoutSession,
    names: Map<String, String>,
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            session.date.format(fullDate),
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WinterCard(accent = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatTile("Sets", "${session.actualSetTotal}", caption = "of ${session.plannedSetTotal} planned")
                    StatTile("Reps", "${session.totalReps}")
                    StatTile("Volume", "${session.totalVolumeKg.roundToInt()}", caption = "kg")
                }
                if (session.extraSetTotal > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${session.extraSetTotal} extra sets beyond the plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Success,
                    )
                }
                session.durationSeconds?.let {
                    Text(
                        "Duration ${it / 60} min ${it % 60} s",
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Muted,
                    )
                }
            }

            session.notes?.takeIf { it.isNotBlank() }?.let {
                WinterCard { Text(it, color = WinterArcColors.Muted) }
            }

            session.exercises.sortedBy { it.position }.forEach { pe ->
                WinterCard(onClick = { onOpenExercise(pe.exerciseId) }) {
                    Text(
                        names[pe.exerciseId] ?: "Exercise",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        buildString {
                            append("Planned ${pe.plannedSets} × ${pe.plannedRepLow}")
                            if (pe.plannedRepHigh != pe.plannedRepLow) append("-${pe.plannedRepHigh}")
                            pe.plannedWeightKg?.takeIf { it > 0 }?.let { append(" @ $it kg") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Muted,
                    )
                    pe.replacedExerciseId?.let {
                        Text(
                            "Replaced ${names[it] ?: "an exercise"} on the day",
                            style = MaterialTheme.typography.bodySmall,
                            color = WinterArcColors.NightBlueBright,
                        )
                    }
                    if (pe.isSkipped) {
                        Text(
                            "Skipped",
                            style = MaterialTheme.typography.bodySmall,
                            color = WinterArcColors.Warning,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    pe.sets.sortedBy { it.setNumber }.forEach { s ->
                        val isExtra = s.setNumber > pe.plannedSets && !s.isWarmup
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (s.isWarmup) "Warm-up" else "Set ${s.setNumber}" +
                                    if (isExtra) "  (extra)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isExtra) WinterArcColors.Success else WinterArcColors.Muted,
                            )
                            Text(
                                "${s.weightKg} kg × ${s.reps}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WinterArcColors.White,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(
    name: String,
    history: ExerciseHistory,
    progression: List<ProgressionPoint>,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = { Text(name) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinterCard(Modifier.weight(1f)) {
                    StatTile("Performed", "${history.timesPerformed}", caption = "sessions")
                }
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        "Heaviest",
                        history.heaviestWeightKg?.let { "$it" } ?: "—",
                        caption = "kg",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinterCard(Modifier.weight(1f)) {
                    StatTile("Best reps", history.bestReps?.toString() ?: "—")
                }
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        "Best session",
                        history.bestSessionVolumeKg?.roundToInt()?.toString() ?: "—",
                        caption = "kg volume",
                    )
                }
            }
            WinterCard {
                StatTile(
                    "Best estimated 1RM",
                    history.bestEstimated1Rm?.let { "${(it * 10).roundToInt() / 10.0}" } ?: "—",
                    caption = "kg — estimated from your best set, never a tested max",
                )
            }
            WinterCard {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatTile("Total sets", "${history.totalSets}")
                    StatTile("Total reps", "${history.totalReps}")
                    StatTile("Volume", "${history.totalVolumeKg.roundToInt()}", caption = "kg")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        history.firstDate?.let { append("First logged ${it.format(shortDate)}") }
                        history.lastDate?.let { append("  ·  last ${it.format(shortDate)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Faint,
                )
            }

            SectionLabel("Progression — estimated 1RM")
            WinterCard {
                LineChart(
                    points = progression.map { ChartPoint(it.date.format(shortDate), it.estimated1Rm) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val unreliable = progression.count { !OneRepMax.isReliable(it.topSetReps) }
                if (unreliable > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$unreliable of ${progression.size} points come from sets above 12 reps, " +
                            "where the 1RM estimate is less reliable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Warning,
                    )
                }
            }

            SectionLabel("Top set weight")
            WinterCard {
                LineChart(
                    points = progression.map { ChartPoint(it.date.format(shortDate), it.topSetWeightKg) },
                    lineColor = WinterArcColors.NightBlueBright,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
