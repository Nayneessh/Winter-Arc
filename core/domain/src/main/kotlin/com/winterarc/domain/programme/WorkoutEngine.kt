package com.winterarc.domain.programme

import com.winterarc.domain.model.ActualSet
import com.winterarc.domain.model.DayType
import com.winterarc.domain.model.PerformedExercise
import com.winterarc.domain.model.SessionStatus
import com.winterarc.domain.model.TrainingStyle
import com.winterarc.domain.model.WorkoutSession
import com.winterarc.domain.model.WorkoutTemplate
import java.time.Instant
import java.time.LocalDate

/** Supplies ids and timestamps so the engine stays pure and fully testable. */
interface Clock {
    fun now(): Instant
    fun today(): LocalDate
}

fun interface IdGenerator {
    fun next(): String
}

/**
 * Pure transformations over a workout session.
 *
 * Every operation returns a NEW session; nothing mutates in place. Two invariants are enforced
 * here rather than trusted to the UI:
 *
 *  1. THE PLAN IS SNAPSHOTTED. [startFromTemplate] copies the prescribed sets/reps/weight/rest
 *     into each [PerformedExercise]. Editing the template afterwards cannot alter a session
 *     that already exists, which is what makes history immutable.
 *
 *  2. COMPLETED SESSIONS ARE FROZEN. Every mutating operation refuses to touch a session whose
 *     status is not IN_PROGRESS, so a finished record cannot be silently rewritten.
 */
class WorkoutEngine(
    private val clock: Clock,
    private val ids: IdGenerator,
) {

    /** Thrown when an operation would modify a session that is no longer in progress. */
    class SessionFrozen(sessionId: String) :
        IllegalStateException("Session $sessionId is completed and cannot be modified.")

    private fun requireEditable(session: WorkoutSession) {
        if (session.status != SessionStatus.IN_PROGRESS) throw SessionFrozen(session.id)
    }

    /**
     * Begins a session from a template, snapshotting the plan.
     * [templateWeights] optionally overrides prescribed weight per planned-exercise id, so the
     * user can adjust the target before starting without editing the template itself.
     */
    fun startFromTemplate(
        template: WorkoutTemplate,
        date: LocalDate = clock.today(),
        templateWeights: Map<String, Double> = emptyMap(),
    ): WorkoutSession {
        val sessionId = ids.next()
        val performed = template.exercises
            .sortedBy { it.position }
            .mapIndexed { index, planned ->
                PerformedExercise(
                    id = ids.next(),
                    sessionId = sessionId,
                    exerciseId = planned.exerciseId,
                    position = index,
                    supersetGroup = planned.supersetGroup,
                    plannedSets = planned.sets,
                    plannedRepLow = planned.repLow,
                    plannedRepHigh = planned.repHigh,
                    plannedWeightKg = templateWeights[planned.id] ?: planned.targetWeightKg,
                    plannedRestSeconds = planned.restSeconds,
                    plannedStyle = planned.style,
                )
            }
        return WorkoutSession(
            id = sessionId,
            templateId = template.id,
            programmeId = template.programmeId,
            name = template.name,
            date = date,
            startedAt = clock.now(),
            status = SessionStatus.IN_PROGRESS,
            dayType = template.dayType,
            exercises = performed,
        )
    }

    /**
     * Begins a session with no template behind it — the "train something else" path.
     * Exercises are added afterwards with [addExercise].
     */
    fun startBlank(
        name: String,
        date: LocalDate = clock.today(),
        dayType: DayType = DayType.TRAINING,
    ): WorkoutSession = WorkoutSession(
        id = ids.next(),
        templateId = null,
        programmeId = null,
        name = name,
        date = date,
        startedAt = clock.now(),
        status = SessionStatus.IN_PROGRESS,
        dayType = dayType,
        exercises = emptyList(),
    )

    // -----------------------------------------------------------------------
    // Sets
    // -----------------------------------------------------------------------

    /**
     * Appends a performed set. Sets beyond the snapshotted plan are simply appended; they are
     * counted as extra by [PerformedExercise.extraSetCount] rather than flagged at write time,
     * so the extra/planned split stays correct however the session is later re-read.
     */
    fun logSet(
        session: WorkoutSession,
        performedExerciseId: String,
        weightKg: Double,
        reps: Int,
        isWarmup: Boolean = false,
        rir: Int? = null,
        notes: String? = null,
    ): WorkoutSession {
        requireEditable(session)
        require(weightKg >= 0.0) { "Weight cannot be negative." }
        require(reps >= 0) { "Reps cannot be negative." }
        return mapExercise(session, performedExerciseId) { pe ->
            val nextNumber = (pe.sets.maxOfOrNull { it.setNumber } ?: 0) + 1
            pe.copy(
                sets = pe.sets + ActualSet(
                    id = ids.next(),
                    performedExerciseId = pe.id,
                    setNumber = nextNumber,
                    weightKg = weightKg,
                    reps = reps,
                    isWarmup = isWarmup,
                    rir = rir,
                    notes = notes,
                    completedAt = clock.now(),
                ),
            )
        }
    }

    fun updateSet(
        session: WorkoutSession,
        setId: String,
        weightKg: Double? = null,
        reps: Int? = null,
        isWarmup: Boolean? = null,
        rir: Int? = null,
        notes: String? = null,
    ): WorkoutSession {
        requireEditable(session)
        weightKg?.let { require(it >= 0.0) { "Weight cannot be negative." } }
        reps?.let { require(it >= 0) { "Reps cannot be negative." } }
        return session.copy(
            exercises = session.exercises.map { pe ->
                if (pe.sets.none { it.id == setId }) pe
                else pe.copy(
                    sets = pe.sets.map { s ->
                        if (s.id != setId) s else s.copy(
                            weightKg = weightKg ?: s.weightKg,
                            reps = reps ?: s.reps,
                            isWarmup = isWarmup ?: s.isWarmup,
                            rir = rir ?: s.rir,
                            notes = notes ?: s.notes,
                        )
                    },
                )
            },
        )
    }

    /** Removes a set and renumbers the remainder so set numbers stay contiguous from 1. */
    fun removeSet(session: WorkoutSession, setId: String): WorkoutSession {
        requireEditable(session)
        return session.copy(
            exercises = session.exercises.map { pe ->
                if (pe.sets.none { it.id == setId }) pe
                else pe.copy(
                    sets = pe.sets
                        .filterNot { it.id == setId }
                        .sortedBy { it.setNumber }
                        .mapIndexed { i, s -> s.copy(setNumber = i + 1) },
                )
            },
        )
    }

    // -----------------------------------------------------------------------
    // Exercises
    // -----------------------------------------------------------------------

    /** Adds a movement that was not in the plan. Its planned figures are the user's own target. */
    fun addExercise(
        session: WorkoutSession,
        exerciseId: String,
        plannedSets: Int = 3,
        plannedRepLow: Int = 8,
        plannedRepHigh: Int = 12,
        plannedWeightKg: Double? = null,
        restSeconds: Int = 90,
        style: TrainingStyle = TrainingStyle.UNSPECIFIED,
        supersetGroup: String? = null,
    ): WorkoutSession {
        requireEditable(session)
        val position = (session.exercises.maxOfOrNull { it.position } ?: -1) + 1
        return session.copy(
            exercises = session.exercises + PerformedExercise(
                id = ids.next(),
                sessionId = session.id,
                exerciseId = exerciseId,
                position = position,
                supersetGroup = supersetGroup,
                plannedSets = plannedSets,
                plannedRepLow = plannedRepLow,
                plannedRepHigh = plannedRepHigh,
                plannedWeightKg = plannedWeightKg,
                plannedRestSeconds = restSeconds,
                plannedStyle = style,
                isAdHoc = true,
            ),
        )
    }

    /**
     * Swaps the movement performed in this slot, for THIS session only.
     *
     * The original exercise id is preserved in [PerformedExercise.replacedExerciseId], so the
     * record shows what was prescribed and what was actually done. Any sets already logged
     * against the slot are cleared, because they belong to the previous movement and carrying
     * them over would attribute one exercise's performance to another.
     *
     * Changing the plan for FUTURE sessions is a template edit, handled by [ProgrammeEditor],
     * and never rewrites past sessions.
     */
    fun replaceExercise(
        session: WorkoutSession,
        performedExerciseId: String,
        newExerciseId: String,
    ): WorkoutSession {
        requireEditable(session)
        return mapExercise(session, performedExerciseId) { pe ->
            pe.copy(
                replacedExerciseId = pe.replacedExerciseId ?: pe.exerciseId,
                exerciseId = newExerciseId,
                sets = emptyList(),
            )
        }
    }

    /** Marks a slot as skipped. The prescription stays on the record; the sets simply do not exist. */
    fun skipExercise(session: WorkoutSession, performedExerciseId: String, skipped: Boolean = true) =
        mapExercise(session.also { requireEditable(it) }, performedExerciseId) {
            it.copy(isSkipped = skipped)
        }

    /** Deletes a slot outright. Use [skipExercise] when the prescription should stay visible. */
    fun removeExercise(session: WorkoutSession, performedExerciseId: String): WorkoutSession {
        requireEditable(session)
        return session.copy(
            exercises = session.exercises
                .filterNot { it.id == performedExerciseId }
                .sortedBy { it.position }
                .mapIndexed { i, pe -> pe.copy(position = i) },
        )
    }

    /** Applies a new order given performed-exercise ids in the desired sequence. */
    fun reorderExercises(session: WorkoutSession, orderedIds: List<String>): WorkoutSession {
        requireEditable(session)
        val byId = session.exercises.associateBy { it.id }
        require(orderedIds.toSet() == byId.keys) {
            "Reorder must list every exercise in the session exactly once."
        }
        return session.copy(
            exercises = orderedIds.mapIndexed { i, id -> byId.getValue(id).copy(position = i) },
        )
    }

    /** Moves one exercise from [fromIndex] to [toIndex] — the drag-and-drop case. */
    fun moveExercise(session: WorkoutSession, fromIndex: Int, toIndex: Int): WorkoutSession {
        requireEditable(session)
        val ordered = session.exercises.sortedBy { it.position }.toMutableList()
        if (fromIndex !in ordered.indices || toIndex !in ordered.indices) return session
        val item = ordered.removeAt(fromIndex)
        ordered.add(toIndex, item)
        return session.copy(exercises = ordered.mapIndexed { i, pe -> pe.copy(position = i) })
    }

    /** Adjusts the target for a slot mid-session without touching the underlying template. */
    fun adjustPlan(
        session: WorkoutSession,
        performedExerciseId: String,
        plannedSets: Int? = null,
        plannedRepLow: Int? = null,
        plannedRepHigh: Int? = null,
        plannedWeightKg: Double? = null,
        restSeconds: Int? = null,
    ): WorkoutSession {
        requireEditable(session)
        return mapExercise(session, performedExerciseId) { pe ->
            pe.copy(
                plannedSets = plannedSets ?: pe.plannedSets,
                plannedRepLow = plannedRepLow ?: pe.plannedRepLow,
                plannedRepHigh = plannedRepHigh ?: pe.plannedRepHigh,
                plannedWeightKg = plannedWeightKg ?: pe.plannedWeightKg,
                plannedRestSeconds = restSeconds ?: pe.plannedRestSeconds,
            )
        }
    }

    fun setSupersetGroup(
        session: WorkoutSession,
        performedExerciseIds: List<String>,
        group: String?,
    ): WorkoutSession {
        requireEditable(session)
        val target = performedExerciseIds.toSet()
        return session.copy(
            exercises = session.exercises.map {
                if (it.id in target) it.copy(supersetGroup = group) else it
            },
        )
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Finalises the session. Slots that were never performed are dropped from the record only
     * if they carry no sets AND were not explicitly skipped, so an untouched prescription does
     * not inflate the "missed" count of a session the user simply cut short.
     */
    fun finish(session: WorkoutSession, notes: String? = null): WorkoutSession {
        requireEditable(session)
        return session.copy(
            status = SessionStatus.COMPLETED,
            finishedAt = clock.now(),
            notes = notes ?: session.notes,
        )
    }

    fun abandon(session: WorkoutSession): WorkoutSession {
        requireEditable(session)
        return session.copy(status = SessionStatus.ABANDONED, finishedAt = clock.now())
    }

    private fun mapExercise(
        session: WorkoutSession,
        performedExerciseId: String,
        transform: (PerformedExercise) -> PerformedExercise,
    ): WorkoutSession = session.copy(
        exercises = session.exercises.map { if (it.id == performedExerciseId) transform(it) else it },
    )
}
