package com.winterarc.domain.analytics

import com.winterarc.domain.model.ActualSet
import com.winterarc.domain.model.PerformedExercise
import com.winterarc.domain.model.WorkoutSession
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Estimated one-rep max.
 *
 * METHODOLOGY — Epley formula:  e1RM = weight x (1 + reps / 30)
 *
 * This matches the formula already used in the source workbook's PR tracker, so figures
 * carried over from it remain comparable.
 *
 * This is an ESTIMATE derived from a submaximal set. It is never a tested 1RM and the UI
 * must always label it as estimated. Accuracy degrades as reps rise; above [RELIABLE_REP_CEILING]
 * reps the estimate is reported but flagged unreliable rather than silently trusted.
 */
object OneRepMax {
    const val RELIABLE_REP_CEILING = 12

    fun epley(weightKg: Double, reps: Int): Double = when {
        reps <= 0 || weightKg <= 0.0 -> 0.0
        // A single rep IS the max; applying the formula would inflate it by 3.3%.
        reps == 1 -> weightKg
        else -> weightKg * (1.0 + reps / 30.0)
    }

    fun epley(set: ActualSet): Double = epley(set.weightKg, set.reps)

    fun isReliable(reps: Int): Boolean = reps in 1..RELIABLE_REP_CEILING
}

/**
 * TRAINING VOLUME — defined throughout this application as:
 *
 *     volume = sum over working sets of (weight_kg x reps)
 *
 * Units are kilogram-reps (kg). Warm-up sets and sets logged with zero reps are excluded:
 * a set that produced no reps produced no volume. Bodyweight movements logged at 0 kg
 * contribute 0 to volume; their progression is tracked by reps and by e1RM instead.
 *
 * This single definition is used by the exercise view, the session summary, the dashboard
 * and every aggregation, so numbers never disagree between screens.
 */
object Volume {
    fun ofSet(set: ActualSet): Double = set.volumeKg
    fun ofExercise(pe: PerformedExercise): Double = pe.volumeKg
    fun ofSession(session: WorkoutSession): Double = session.totalVolumeKg

    fun bySession(sessions: List<WorkoutSession>): Map<String, Double> =
        sessions.associate { it.id to it.totalVolumeKg }

    fun byExercise(sessions: List<WorkoutSession>): Map<String, Double> {
        val acc = mutableMapOf<String, Double>()
        sessions.flatMap { it.performedExercises }.forEach { pe ->
            acc[pe.exerciseId] = (acc[pe.exerciseId] ?: 0.0) + pe.volumeKg
        }
        return acc
    }

    /** Weekly totals keyed by the Monday of each ISO week. */
    fun byWeek(sessions: List<WorkoutSession>): Map<LocalDate, Double> {
        val acc = sortedMapOf<LocalDate, Double>()
        sessions.forEach { s ->
            val monday = s.date.minusDays((s.date.dayOfWeek.value - 1).toLong())
            acc[monday] = (acc[monday] ?: 0.0) + s.totalVolumeKg
        }
        return acc
    }

    /** Monthly totals keyed by the first day of each month. */
    fun byMonth(sessions: List<WorkoutSession>): Map<LocalDate, Double> {
        val acc = sortedMapOf<LocalDate, Double>()
        sessions.forEach { s ->
            val first = s.date.withDayOfMonth(1)
            acc[first] = (acc[first] ?: 0.0) + s.totalVolumeKg
        }
        return acc
    }
}

enum class PrType(val label: String) {
    WEIGHT("Weight PR"),
    REPS("Rep PR"),
    ESTIMATED_1RM("Est. 1RM PR"),
    SESSION_VOLUME("Volume PR"),
}

data class PersonalRecord(
    val type: PrType,
    val exerciseId: String,
    val sessionId: String,
    val date: LocalDate,
    val value: Double,
    val previousValue: Double?,
    val weightKg: Double? = null,
    val reps: Int? = null,
) {
    val improvement: Double? get() = previousValue?.let { value - it }
    val isFirstEver: Boolean get() = previousValue == null
}

/**
 * Detects personal records for an exercise by comparing a candidate session against the
 * user's ACTUAL prior history for that same exercise.
 *
 * Rules, chosen so that a PR always means something:
 *  - WEIGHT       heaviest working set strictly exceeds every previous working set.
 *  - REPS         more reps at THE SAME WEIGHT than ever achieved at that weight before.
 *                 Requiring an exact weight match keeps the signal clean: a light high-rep
 *                 set cannot out-rank a heavy low-rep one, and a weight never lifted before
 *                 is reported through WEIGHT rather than manufacturing a rep record with no
 *                 basis for comparison. It also makes bodyweight movements work correctly,
 *                 where every set sits at the same load and reps are the progression.
 *  - EST_1RM      Epley estimate exceeds the best previous estimate.
 *  - VOLUME       total volume for this exercise in this session exceeds any previous session.
 *
 * A first-ever performance produces PRs with [PersonalRecord.previousValue] = null. That is
 * deliberate and truthful: it is a record, and the UI can present it as a baseline rather
 * than as an improvement.
 */
object PrDetector {

    fun detect(
        exerciseId: String,
        candidate: WorkoutSession,
        history: List<WorkoutSession>,
    ): List<PersonalRecord> {
        val candidateSets = candidate.performedExercises
            .filter { it.exerciseId == exerciseId }
            .flatMap { it.workingSets }
        if (candidateSets.isEmpty()) return emptyList()

        // Only sessions strictly BEFORE the candidate count as history, and never itself.
        val priorSessions = history
            .filter { it.id != candidate.id && it.date.isBefore(candidate.date) }
        val priorSets = priorSessions
            .flatMap { s -> s.performedExercises.filter { it.exerciseId == exerciseId } }
            .flatMap { it.workingSets }

        val records = mutableListOf<PersonalRecord>()

        // --- WEIGHT ---
        val bestWeightSet = candidateSets.maxByOrNull { it.weightKg }!!
        val priorBestWeight = priorSets.maxOfOrNull { it.weightKg }
        if (bestWeightSet.weightKg > 0.0 && (priorBestWeight == null || bestWeightSet.weightKg > priorBestWeight)) {
            records += PersonalRecord(
                type = PrType.WEIGHT,
                exerciseId = exerciseId,
                sessionId = candidate.id,
                date = candidate.date,
                value = bestWeightSet.weightKg,
                previousValue = priorBestWeight,
                weightKg = bestWeightSet.weightKg,
                reps = bestWeightSet.reps,
            )
        }

        // --- REPS (same weight, beaten) ---
        // Best reps previously achieved at each exact load.
        val priorBestRepsByWeight: Map<Double, Int> = priorSets
            .groupBy { it.weightKg }
            .mapValues { (_, sets) -> sets.maxOf { it.reps } }

        var bestRepRecord: PersonalRecord? = null
        candidateSets.forEach { set ->
            val priorReps = priorBestRepsByWeight[set.weightKg]
            // A load never used before has no rep baseline; WEIGHT covers that case instead.
            if (priorReps != null && set.reps > priorReps &&
                set.reps > (bestRepRecord?.reps ?: 0)
            ) {
                bestRepRecord = PersonalRecord(
                    type = PrType.REPS,
                    exerciseId = exerciseId,
                    sessionId = candidate.id,
                    date = candidate.date,
                    value = set.reps.toDouble(),
                    previousValue = priorReps.toDouble(),
                    weightKg = set.weightKg,
                    reps = set.reps,
                )
            }
        }
        bestRepRecord?.let { records += it }

        // --- ESTIMATED 1RM ---
        val bestE1rmSet = candidateSets.maxByOrNull { OneRepMax.epley(it) }!!
        val candidateE1rm = OneRepMax.epley(bestE1rmSet)
        val priorBestE1rm = priorSets.maxOfOrNull { OneRepMax.epley(it) }
        if (candidateE1rm > 0.0 && (priorBestE1rm == null || candidateE1rm > priorBestE1rm)) {
            records += PersonalRecord(
                type = PrType.ESTIMATED_1RM,
                exerciseId = exerciseId,
                sessionId = candidate.id,
                date = candidate.date,
                value = candidateE1rm,
                previousValue = priorBestE1rm,
                weightKg = bestE1rmSet.weightKg,
                reps = bestE1rmSet.reps,
            )
        }

        // --- SESSION VOLUME ---
        val candidateVolume = candidate.performedExercises
            .filter { it.exerciseId == exerciseId }
            .sumOf { it.volumeKg }
        val priorBestVolume = priorSessions
            .map { s -> s.performedExercises.filter { it.exerciseId == exerciseId }.sumOf { it.volumeKg } }
            .filter { it > 0.0 }
            .maxOrNull()
        if (candidateVolume > 0.0 && (priorBestVolume == null || candidateVolume > priorBestVolume)) {
            records += PersonalRecord(
                type = PrType.SESSION_VOLUME,
                exerciseId = exerciseId,
                sessionId = candidate.id,
                date = candidate.date,
                value = candidateVolume,
                previousValue = priorBestVolume,
            )
        }

        return records
    }

    /** Detects PRs across every exercise touched by [candidate]. */
    fun detectAll(candidate: WorkoutSession, history: List<WorkoutSession>): List<PersonalRecord> =
        candidate.performedExercises
            .map { it.exerciseId }
            .distinct()
            .flatMap { detect(it, candidate, history) }
}

/** Aggregated lifetime view of one movement. */
data class ExerciseHistory(
    val exerciseId: String,
    val timesPerformed: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val heaviestWeightKg: Double?,
    val bestReps: Int?,
    val bestSessionVolumeKg: Double?,
    val bestEstimated1Rm: Double?,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val totalReps: Int,
) {
    companion object {
        fun from(exerciseId: String, sessions: List<WorkoutSession>): ExerciseHistory {
            val relevant = sessions
                .filter { s -> s.performedExercises.any { it.exerciseId == exerciseId } }
                .sortedBy { it.date }
            val sets = relevant
                .flatMap { s -> s.performedExercises.filter { it.exerciseId == exerciseId } }
                .flatMap { it.workingSets }
            return ExerciseHistory(
                exerciseId = exerciseId,
                timesPerformed = relevant.size,
                firstDate = relevant.firstOrNull()?.date,
                lastDate = relevant.lastOrNull()?.date,
                heaviestWeightKg = sets.maxOfOrNull { it.weightKg },
                bestReps = sets.maxOfOrNull { it.reps },
                bestSessionVolumeKg = relevant.maxOfOrNull { s ->
                    s.performedExercises.filter { it.exerciseId == exerciseId }.sumOf { it.volumeKg }
                },
                bestEstimated1Rm = sets.maxOfOrNull { OneRepMax.epley(it) },
                totalVolumeKg = sets.sumOf { it.volumeKg },
                totalSets = sets.size,
                totalReps = sets.sumOf { it.reps },
            )
        }
    }
}

/** A single point on an exercise progression chart. */
data class ProgressionPoint(
    val date: LocalDate,
    val topSetWeightKg: Double,
    val topSetReps: Int,
    val estimated1Rm: Double,
    val volumeKg: Double,
)

object Progression {
    /** Chronological progression for one movement; one point per session it appeared in. */
    fun forExercise(exerciseId: String, sessions: List<WorkoutSession>): List<ProgressionPoint> =
        sessions
            .filter { s -> s.performedExercises.any { it.exerciseId == exerciseId } }
            .sortedBy { it.date }
            .mapNotNull { s ->
                val sets = s.performedExercises
                    .filter { it.exerciseId == exerciseId }
                    .flatMap { it.workingSets }
                if (sets.isEmpty()) return@mapNotNull null
                val top = sets.maxByOrNull { OneRepMax.epley(it) }!!
                ProgressionPoint(
                    date = s.date,
                    topSetWeightKg = top.weightKg,
                    topSetReps = top.reps,
                    estimated1Rm = OneRepMax.epley(top),
                    volumeKg = sets.sumOf { it.volumeKg },
                )
            }
}

/** Rounds to one decimal place for display without dragging formatting into the UI layer. */
fun Double.round1(): Double = (this * 10).roundToInt() / 10.0
