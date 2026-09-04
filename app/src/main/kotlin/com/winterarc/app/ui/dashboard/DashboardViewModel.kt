package com.winterarc.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.winterarc.app.AppContainer
import com.winterarc.domain.analytics.*
import com.winterarc.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DateRange(val label: String, val days: Long?) {
    D30("30d", 30), D90("90d", 90), M6("6m", 182), Y1("1y", 365), ALL("All", null)
}

data class DashboardUiState(
    val loading: Boolean = true,
    val totalWorkouts: Int = 0,
    val workoutsThisMonth: Int = 0,
    val weekStreak: Int = 0,
    val longestStreak: Int = 0,
    val avgPerWeek: Double = 0.0,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val exercisesPerformed: Int = 0,
    val customExercisesAdded: Int = 0,
    val recentPrs: List<Pair<PersonalRecord, String>> = emptyList(),
    val volumeByWeek: List<Pair<LocalDate, Double>> = emptyList(),
    val muscleShares: List<MuscleVolumeShare> = emptyList(),
    val goals: List<GoalProgress> = emptyList(),
    val trackedExerciseId: String? = null,
    val trackedExerciseName: String = "",
    val progression: List<ProgressionPoint> = emptyList(),
    val exerciseOptions: List<Pair<String, String>> = emptyList(),
    val range: DateRange = DateRange.D90,
    val bodyWeightSeries: List<Pair<LocalDate, Double>> = emptyList(),
)

/**
 * Assembles the dashboard.
 *
 * All computation is delegated to the domain analytics objects so the figures shown here are
 * produced by the same code the unit tests cover — the dashboard cannot drift from the
 * numbers proven correct elsewhere.
 */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init { refresh() }

    fun setRange(range: DateRange) {
        _state.value = _state.value.copy(range = range)
        refresh()
    }

    fun trackExercise(exerciseId: String) {
        _state.value = _state.value.copy(trackedExerciseId = exerciseId)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val allHistory = repo.completedHistory()
            val library = repo.allExercises()
            val names = library.associate { it.id to it.name }
            val muscles = library.associate { it.id to it.primaryMuscle }
            val range = _state.value.range

            val cutoff = range.days?.let { today.minusDays(it) }
            val history = if (cutoff == null) allHistory else allHistory.filter { !it.date.isBefore(cutoff) }

            val prs = allHistory
                .sortedBy { it.date }
                .flatMap { s -> PrDetector.detectAll(s, allHistory.filter { it.date < s.date }) }
                .sortedByDescending { it.date }
                .take(8)
                .map { it to (names[it.exerciseId] ?: "Exercise") }

            val performedIds = allHistory.flatMap { it.performedExercises }.map { it.exerciseId }.distinct()

            // The exercise shown in the progression chart defaults to the most-trained one.
            val tracked = _state.value.trackedExerciseId
                ?: allHistory.flatMap { it.performedExercises }
                    .groupingBy { it.exerciseId }.eachCount()
                    .maxByOrNull { it.value }?.key

            val body = repo.allBodyMetrics()
            val weightSeries = body
                .filter { it.weightKg != null }
                .filter { cutoff == null || !it.date.isBefore(cutoff) }
                .sortedBy { it.date }
                .map { it.date to it.weightKg!! }

            _state.value = DashboardUiState(
                loading = false,
                totalWorkouts = allHistory.size,
                workoutsThisMonth = Consistency.sessionsInMonthOf(allHistory, today),
                weekStreak = Consistency.currentWeekStreak(allHistory, today),
                longestStreak = Consistency.longestWeekStreak(allHistory),
                avgPerWeek = Consistency.averageSessionsPerWeek(allHistory, today, 4),
                totalSets = history.sumOf { it.actualSetTotal },
                totalReps = history.sumOf { it.totalReps },
                totalVolumeKg = history.sumOf { it.totalVolumeKg },
                exercisesPerformed = performedIds.size,
                customExercisesAdded = library.count { it.isCustom },
                recentPrs = prs,
                volumeByWeek = Volume.byWeek(history).toList(),
                muscleShares = VolumeComposition.byMuscleGroup(history) { muscles[it] },
                goals = buildGoals(allHistory, body, names),
                trackedExerciseId = tracked,
                trackedExerciseName = tracked?.let { names[it] } ?: "",
                progression = tracked?.let { Progression.forExercise(it, history) } ?: emptyList(),
                exerciseOptions = performedIds.map { it to (names[it] ?: "Exercise") }.sortedBy { it.second },
                range = range,
                bodyWeightSeries = weightSeries,
            )
        }
    }

    /**
     * Goal rings.
     *
     * Targets come from the programme's own stated milestones. A goal is only shown when the
     * user has actually logged the lift — a ring at 0% for something never attempted is noise,
     * not information.
     */
    private fun buildGoals(
        history: List<WorkoutSession>,
        body: List<BodyMetric>,
        names: Map<String, String>,
    ): List<GoalProgress> {
        val goals = mutableListOf<GoalProgress>()

        data class LiftGoal(val exerciseId: String, val label: String, val start: Double, val target: Double)

        val liftGoals = listOf(
            LiftGoal("ex-standing-db-curl", "DB Curl", 17.5, 21.0),
            LiftGoal("ex-close-grip-bench", "Close-Grip Bench", 50.0, 57.0),
            LiftGoal("ex-incline-press", "Incline Press", 55.0, 62.0),
        )

        liftGoals.forEach { g ->
            val best = history
                .flatMap { s -> s.performedExercises.filter { it.exerciseId == g.exerciseId } }
                .flatMap { it.workingSets }
                .maxOfOrNull { it.weightKg }
            if (best != null) {
                goals += GoalProgress(
                    label = names[g.exerciseId] ?: g.label,
                    startValue = g.start,
                    currentValue = best,
                    targetValue = g.target,
                    unit = "kg",
                )
            }
        }

        val weights = body.mapNotNull { m -> m.weightKg?.let { m.date to it } }.sortedBy { it.first }
        if (weights.size >= 2) {
            goals += GoalProgress(
                label = "Bodyweight",
                startValue = weights.first().second,
                currentValue = weights.last().second,
                targetValue = 73.0,
                unit = "kg",
            )
        }
        return goals
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(container) as T
    }
}
