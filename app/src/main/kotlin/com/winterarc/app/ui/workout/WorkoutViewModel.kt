package com.winterarc.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.winterarc.app.AppContainer
import com.winterarc.domain.analytics.PersonalRecord
import com.winterarc.domain.analytics.PrDetector
import com.winterarc.domain.model.*
import com.winterarc.domain.programme.WorkoutEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Full-screen rest countdown state. Independent of any single exercise so it survives navigation. */
data class TimerState(
    val isActive: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isPaused: Boolean = false,
    val exerciseName: String = "",
    val justFinished: Boolean = false,
) {
    val progress: Float
        get() = if (totalSeconds <= 0) 0f else remainingSeconds.toFloat() / totalSeconds.toFloat()

    val display: String
        get() {
            val s = remainingSeconds.coerceAtLeast(0)
            return "%02d:%02d".format(s / 60, s % 60)
        }
}

data class WorkoutUiState(
    val session: WorkoutSession? = null,
    val exerciseNames: Map<String, String> = emptyMap(),
    val library: List<Exercise> = emptyList(),
    val previousPerformance: Map<String, PerformedExercise> = emptyMap(),
    val currentIndex: Int = 0,
    val loading: Boolean = true,
    val finished: Boolean = false,
    val newRecords: List<PersonalRecord> = emptyList(),
    val message: String? = null,
) {
    val current: PerformedExercise?
        get() = session?.exercises?.sortedBy { it.position }?.getOrNull(currentIndex)

    fun nameOf(exerciseId: String): String = exerciseNames[exerciseId] ?: "Exercise"
}

/**
 * Drives an in-progress workout.
 *
 * Every mutation goes through the domain [WorkoutEngine] and is persisted immediately
 * afterwards. Saving on every change rather than at the end is intentional: a phone that is
 * killed mid-workout, or a user who presses back by accident, must not lose logged sets.
 */
class WorkoutViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val engine = container.workoutEngine

    private val _state = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private var timerJob: Job? = null
    private var autoStartTimer: Boolean = true

    init {
        viewModelScope.launch {
            container.settings.settings.collect { autoStartTimer = it.autoStartRestTimer }
        }
    }

    // -----------------------------------------------------------------------
    // Loading and starting
    // -----------------------------------------------------------------------

    /** Resumes an unfinished session if one exists, otherwise starts today's template. */
    fun loadOrStart(templateId: String?) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val existing = repo.inProgressSessionId()?.let { repo.loadSession(it) }
            val session = existing ?: when {
                templateId != null -> repo.templateWithExercises(templateId)
                    ?.let { engine.startFromTemplate(it) }
                else -> repo.templateForDate(LocalDate.now())?.let { engine.startFromTemplate(it) }
            }
            if (session == null) {
                _state.update {
                    it.copy(loading = false, message = "No workout is scheduled for today. Start a custom one instead.")
                }
                return@launch
            }
            if (existing == null) repo.saveSession(session)
            hydrate(session)
        }
    }

    fun startCustom(name: String) {
        viewModelScope.launch {
            val session = engine.startBlank(name)
            repo.saveSession(session)
            hydrate(session)
        }
    }

    fun startFromTemplate(templateId: String) {
        viewModelScope.launch {
            val template = repo.templateWithExercises(templateId) ?: return@launch
            val session = engine.startFromTemplate(template)
            repo.saveSession(session)
            hydrate(session)
        }
    }

    private suspend fun hydrate(session: WorkoutSession) {
        val library = repo.allExercises()
        val names = library.associate { it.id to it.name }
        val previous = session.exercises.associate { pe ->
            pe.exerciseId to repo.lastPerformance(pe.exerciseId, beforeSessionId = session.id)
        }.filterValues { it != null }.mapValues { it.value!! }

        _state.update {
            it.copy(
                session = session,
                exerciseNames = names,
                library = library,
                previousPerformance = previous,
                loading = false,
                // Land on the first exercise that still has sets outstanding.
                currentIndex = session.exercises
                    .sortedBy { e -> e.position }
                    .indexOfFirst { e -> !e.isSkipped && e.actualSetCount < e.plannedSets }
                    .takeIf { i -> i >= 0 } ?: 0,
            )
        }
    }

    private fun apply(transform: (WorkoutSession) -> WorkoutSession) {
        val current = _state.value.session ?: return
        val updated = try {
            transform(current)
        } catch (e: WorkoutEngine.SessionFrozen) {
            _state.update { it.copy(message = "This workout is already finished.") }
            return
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(message = e.message) }
            return
        }
        _state.update { it.copy(session = updated) }
        viewModelScope.launch { repo.saveSession(updated) }
    }

    // -----------------------------------------------------------------------
    // Set logging
    // -----------------------------------------------------------------------

    fun logSet(performedExerciseId: String, weightKg: Double, reps: Int, isWarmup: Boolean = false) {
        apply { engine.logSet(it, performedExerciseId, weightKg, reps, isWarmup) }
        if (autoStartTimer && !isWarmup) {
            val pe = _state.value.session?.exercises?.firstOrNull { it.id == performedExerciseId }
            if (pe != null) startTimer(pe.plannedRestSeconds, _state.value.nameOf(pe.exerciseId))
        }
    }

    fun updateSet(setId: String, weightKg: Double? = null, reps: Int? = null) =
        apply { engine.updateSet(it, setId, weightKg, reps) }

    fun removeSet(setId: String) = apply { engine.removeSet(it, setId) }

    // -----------------------------------------------------------------------
    // Exercise operations
    // -----------------------------------------------------------------------

    fun addExercise(exerciseId: String, sets: Int, repLow: Int, repHigh: Int, restSeconds: Int) =
        apply { engine.addExercise(it, exerciseId, sets, repLow, repHigh, restSeconds = restSeconds) }

    fun replaceExercise(performedExerciseId: String, newExerciseId: String) =
        apply { engine.replaceExercise(it, performedExerciseId, newExerciseId) }

    fun removeExercise(performedExerciseId: String) {
        apply { engine.removeExercise(it, performedExerciseId) }
        _state.update {
            val size = it.session?.exercises?.size ?: 0
            it.copy(currentIndex = it.currentIndex.coerceAtMost((size - 1).coerceAtLeast(0)))
        }
    }

    fun skipExercise(performedExerciseId: String, skipped: Boolean = true) =
        apply { engine.skipExercise(it, performedExerciseId, skipped) }

    fun moveExercise(from: Int, to: Int) = apply { engine.moveExercise(it, from, to) }

    fun adjustPlan(
        performedExerciseId: String,
        sets: Int? = null,
        repLow: Int? = null,
        repHigh: Int? = null,
        weightKg: Double? = null,
        restSeconds: Int? = null,
    ) = apply { engine.adjustPlan(it, performedExerciseId, sets, repLow, repHigh, weightKg, restSeconds) }

    fun setSuperset(ids: List<String>, group: String?) =
        apply { engine.setSupersetGroup(it, ids, group) }

    fun selectExercise(index: Int) = _state.update { it.copy(currentIndex = index) }

    fun next() = _state.update {
        val size = it.session?.exercises?.size ?: 0
        it.copy(currentIndex = (it.currentIndex + 1).coerceAtMost((size - 1).coerceAtLeast(0)))
    }

    fun previous() = _state.update { it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0)) }

    /** Creates a custom movement and immediately adds it to the session. */
    fun createAndAddExercise(
        name: String,
        muscle: MuscleGroup,
        equipment: Equipment,
        restSeconds: Int,
        notes: String?,
    ) {
        viewModelScope.launch {
            val exercise = Exercise(
                id = container.ids.next(),
                name = name.trim(),
                primaryMuscle = muscle,
                equipment = equipment,
                defaultRestSeconds = restSeconds,
                notes = notes?.takeIf { it.isNotBlank() },
                isCustom = true,
            )
            repo.saveExercise(exercise)
            val library = repo.allExercises()
            _state.update {
                it.copy(library = library, exerciseNames = library.associate { e -> e.id to e.name })
            }
            addExercise(exercise.id, 3, 8, 12, restSeconds)
        }
    }

    // -----------------------------------------------------------------------
    // Finish
    // -----------------------------------------------------------------------

    fun finishWorkout(notes: String? = null) {
        val current = _state.value.session ?: return
        viewModelScope.launch {
            val finished = try {
                engine.finish(current, notes)
            } catch (e: WorkoutEngine.SessionFrozen) {
                current
            }
            repo.saveSession(finished)
            val history = repo.completedHistory().filter { it.id != finished.id }
            val prs = PrDetector.detectAll(finished, history)
            stopTimer()
            _state.update { it.copy(session = finished, finished = true, newRecords = prs) }
        }
    }

    fun abandonWorkout() {
        val current = _state.value.session ?: return
        viewModelScope.launch {
            runCatching { repo.saveSession(engine.abandon(current)) }
            stopTimer()
            _state.update { WorkoutUiState(loading = false) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // -----------------------------------------------------------------------
    // Rest timer
    // -----------------------------------------------------------------------

    /**
     * The countdown runs off a monotonic deadline rather than by decrementing a counter, so
     * it stays accurate if the coroutine is delayed or the screen turns off mid-rest.
     */
    fun startTimer(seconds: Int, exerciseName: String) {
        if (seconds <= 0) return
        timerJob?.cancel()
        _timer.value = TimerState(
            isActive = true,
            totalSeconds = seconds,
            remainingSeconds = seconds,
            exerciseName = exerciseName,
        )
        timerJob = viewModelScope.launch { runCountdown() }
    }

    private suspend fun runCountdown() {
        var deadline = System.currentTimeMillis() + _timer.value.remainingSeconds * 1000L
        while (true) {
            delay(200)
            val s = _timer.value
            if (!s.isActive) return
            if (s.isPaused) {
                deadline = System.currentTimeMillis() + s.remainingSeconds * 1000L
                continue
            }
            val remaining = ((deadline - System.currentTimeMillis()) / 1000.0).let {
                kotlin.math.ceil(it).toInt()
            }
            if (remaining <= 0) {
                _timer.update { it.copy(remainingSeconds = 0, isActive = false, justFinished = true) }
                return
            }
            _timer.update { it.copy(remainingSeconds = remaining) }
        }
    }

    fun pauseTimer() = _timer.update { it.copy(isPaused = true) }

    fun resumeTimer() {
        _timer.update { it.copy(isPaused = false) }
        if (timerJob?.isActive != true && _timer.value.isActive) {
            timerJob = viewModelScope.launch { runCountdown() }
        }
    }

    fun adjustTimer(deltaSeconds: Int) = _timer.update {
        val remaining = (it.remainingSeconds + deltaSeconds).coerceAtLeast(0)
        it.copy(
            remainingSeconds = remaining,
            // Growing the total keeps the progress ring honest when time is added.
            totalSeconds = maxOf(it.totalSeconds, remaining),
        )
    }

    fun skipTimer() = stopTimer()

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timer.value = TimerState()
    }

    fun acknowledgeTimerFinished() = _timer.update { it.copy(justFinished = false) }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WorkoutViewModel(container) as T
    }
}
