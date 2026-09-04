package com.winterarc.domain.model

import java.time.Instant
import java.time.LocalDate

/** How a set is intended to be executed. Affects nothing computationally; it is coaching context. */
enum class TrainingStyle {
    HEAVY_STRENGTH, HYPERTROPHY, LENGTHENED, SHORTENED, PUMP, MODERATE, UNSPECIFIED;

    val label: String
        get() = when (this) {
            HEAVY_STRENGTH -> "Heavy"
            HYPERTROPHY -> "Hypertrophy"
            LENGTHENED -> "Lengthened"
            SHORTENED -> "Shortened"
            PUMP -> "Pump"
            MODERATE -> "Moderate"
            UNSPECIFIED -> "—"
        }
}

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS, QUADS, HAMSTRINGS,
    GLUTES, CALVES, ABS, TRAPS, NECK, FULL_BODY, OTHER;

    val label: String
        get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

enum class Equipment {
    BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, SMITH, KETTLEBELL, BAND, OTHER;

    val label: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/** Day classification. Not every day has a workout. */
enum class DayType { TRAINING, REST, ACTIVE_RECOVERY, MMA, CUSTOM }

/**
 * A movement in the user's library. Seeded entries come from the source programme;
 * user-created entries carry [isCustom] = true. Nothing is ever hard-coded to a screen.
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val defaultRestSeconds: Int = 90,
    val notes: String? = null,
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,
)

/**
 * A prescribed exercise inside a template. This is the PLAN.
 * [supersetGroup] pairs movements: entries sharing a non-null group are performed together
 * (the workbook's A1/A2, B1/B2 notation).
 */
data class PlannedExercise(
    val id: String,
    val exerciseId: String,
    val position: Int,
    val supersetGroup: String? = null,
    val sets: Int,
    val repLow: Int,
    val repHigh: Int,
    val targetWeightKg: Double? = null,
    val rir: String? = null,
    val restSeconds: Int,
    val style: TrainingStyle = TrainingStyle.UNSPECIFIED,
    val notes: String? = null,
) {
    val repRangeLabel: String get() = if (repLow == repHigh) "$repLow" else "$repLow-$repHigh"
}

/**
 * A reusable workout definition (e.g. "Tuesday — Upper Push + Arms").
 * Editable at runtime: the user changes days, order, sets, reps, pairings without a code change.
 */
data class WorkoutTemplate(
    val id: String,
    val programmeId: String,
    val name: String,
    val dayOfWeek: Int?,
    val dayType: DayType = DayType.TRAINING,
    val muscleGroups: List<MuscleGroup> = emptyList(),
    val position: Int = 0,
    val notes: String? = null,
    val exercises: List<PlannedExercise> = emptyList(),
    val isArchived: Boolean = false,
) {
    val plannedSetCount: Int get() = exercises.sumOf { it.sets }
}

/** A named collection of templates — the training block the user is currently running. */
data class Programme(
    val id: String,
    val name: String,
    val description: String? = null,
    val isActive: Boolean = false,
    val templates: List<WorkoutTemplate> = emptyList(),
)

// ---------------------------------------------------------------------------
// ACTUAL performance
// ---------------------------------------------------------------------------

/**
 * One set that was actually performed. This is immutable history once its session is finished.
 *
 * [isWarmup] sets are recorded but excluded from volume, PR and analytics computations.
 */
data class ActualSet(
    val id: String,
    val performedExerciseId: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
    val rir: Int? = null,
    val notes: String? = null,
    val completedAt: Instant? = null,
) {
    /** Volume contribution of this set. Warm-ups and zero-rep sets contribute nothing. */
    val volumeKg: Double get() = if (isWarmup || reps <= 0) 0.0 else weightKg * reps
    val isWorkingSet: Boolean get() = !isWarmup && reps > 0
}

/**
 * An exercise as it appeared inside a real session.
 *
 * The planned figures are SNAPSHOTTED here at session start. That is what makes history
 * immutable: editing the template later cannot rewrite what a past session prescribed.
 */
data class PerformedExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val supersetGroup: String? = null,
    // --- plan snapshot (never mutated after the session is finished) ---
    val plannedSets: Int,
    val plannedRepLow: Int,
    val plannedRepHigh: Int,
    val plannedWeightKg: Double? = null,
    val plannedRestSeconds: Int,
    val plannedStyle: TrainingStyle = TrainingStyle.UNSPECIFIED,
    // --- provenance ---
    val replacedExerciseId: String? = null,
    val isAdHoc: Boolean = false,
    val isSkipped: Boolean = false,
    val notes: String? = null,
    val sets: List<ActualSet> = emptyList(),
) {
    val workingSets: List<ActualSet> get() = sets.filter { it.isWorkingSet }
    val actualSetCount: Int get() = workingSets.size

    /** Sets performed beyond what the plan prescribed. Never negative. */
    val extraSetCount: Int get() = (actualSetCount - plannedSets).coerceAtLeast(0)

    /** Prescribed sets that were not performed. Never negative. */
    val missedSetCount: Int get() = (plannedSets - actualSetCount).coerceAtLeast(0)

    val totalReps: Int get() = workingSets.sumOf { it.reps }
    val volumeKg: Double get() = workingSets.sumOf { it.volumeKg }
    val heaviestSet: ActualSet? get() = workingSets.maxByOrNull { it.weightKg }
    val wasReplaced: Boolean get() = replacedExerciseId != null
}

enum class SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }

/**
 * A real training session. Once [status] is COMPLETED this is a permanent record.
 * [templateId] may be null — the user can train something entirely different.
 */
data class WorkoutSession(
    val id: String,
    val templateId: String?,
    val programmeId: String?,
    val name: String,
    val date: LocalDate,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val dayType: DayType = DayType.TRAINING,
    val notes: String? = null,
    val exercises: List<PerformedExercise> = emptyList(),
) {
    val performedExercises: List<PerformedExercise> get() = exercises.filterNot { it.isSkipped }

    val durationSeconds: Long?
        get() = finishedAt?.let { it.epochSecond - startedAt.epochSecond }

    val plannedSetTotal: Int get() = exercises.sumOf { it.plannedSets }
    val actualSetTotal: Int get() = performedExercises.sumOf { it.actualSetCount }
    val extraSetTotal: Int get() = performedExercises.sumOf { it.extraSetCount }
    val totalReps: Int get() = performedExercises.sumOf { it.totalReps }
    val totalVolumeKg: Double get() = performedExercises.sumOf { it.volumeKg }

    val muscleGroupsTrained: Set<String>
        get() = performedExercises.map { it.exerciseId }.toSet()
}
