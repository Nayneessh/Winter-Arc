package com.winterarc.domain.analytics

import com.winterarc.domain.model.MuscleGroup
import com.winterarc.domain.model.SessionStatus
import com.winterarc.domain.model.WorkoutSession
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Training consistency.
 *
 * STREAK DEFINITION: a streak counts CONSECUTIVE WEEKS containing at least one completed
 * session, not consecutive days.
 *
 * Reasoning: the programme this app was built around prescribes a mandatory Sunday rest day.
 * A day-based streak would therefore reset every single week by design, punishing the user for
 * following the plan correctly. A week-based streak measures the thing that actually matters —
 * whether training is still happening — and cannot be broken by planned recovery.
 */
object Consistency {

    fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /**
     * Consecutive weeks with at least one completed session, counting back from the week
     * containing [today]. The current week is allowed to be empty without breaking the streak
     * (the week is not over yet); the count then resumes from last week.
     */
    fun currentWeekStreak(sessions: List<WorkoutSession>, today: LocalDate): Int {
        val weeks = sessions
            .filter { it.status == SessionStatus.COMPLETED }
            .map { mondayOf(it.date) }
            .toSet()
        if (weeks.isEmpty()) return 0

        val thisWeek = mondayOf(today)
        var cursor = if (weeks.contains(thisWeek)) thisWeek else thisWeek.minusWeeks(1)
        if (!weeks.contains(cursor)) return 0

        var streak = 0
        while (weeks.contains(cursor)) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    fun longestWeekStreak(sessions: List<WorkoutSession>): Int {
        val weeks = sessions
            .filter { it.status == SessionStatus.COMPLETED }
            .map { mondayOf(it.date) }
            .distinct()
            .sorted()
        if (weeks.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until weeks.size) {
            run = if (ChronoUnit.WEEKS.between(weeks[i - 1], weeks[i]) == 1L) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    fun sessionsInWeekOf(sessions: List<WorkoutSession>, date: LocalDate): Int {
        val monday = mondayOf(date)
        return sessions.count {
            it.status == SessionStatus.COMPLETED && mondayOf(it.date) == monday
        }
    }

    fun sessionsInMonthOf(sessions: List<WorkoutSession>, date: LocalDate): Int =
        sessions.count {
            it.status == SessionStatus.COMPLETED &&
                it.date.year == date.year && it.date.month == date.month
        }

    /** Mean completed sessions per week over the trailing [weeks] weeks, inclusive of this one. */
    fun averageSessionsPerWeek(
        sessions: List<WorkoutSession>,
        today: LocalDate,
        weeks: Int = 4,
    ): Double {
        if (weeks <= 0) return 0.0
        val earliestMonday = mondayOf(today).minusWeeks((weeks - 1).toLong())
        val count = sessions.count {
            it.status == SessionStatus.COMPLETED && !it.date.isBefore(earliestMonday)
        }
        return count.toDouble() / weeks
    }

    fun daysSinceLastSession(sessions: List<WorkoutSession>, today: LocalDate): Long? =
        sessions.filter { it.status == SessionStatus.COMPLETED }
            .maxByOrNull { it.date }
            ?.let { ChronoUnit.DAYS.between(it.date, today) }
}

/**
 * One slice of the muscle-group volume donut.
 *
 * WHY A DONUT IS VALID HERE: volume share is a genuine part-to-whole breakdown — every slice
 * is measured in the same unit (kg) and the slices sum to total training volume. That is
 * exactly the condition a pie/donut requires.
 *
 * It is NOT used for "progress composition" (strength vs weight vs rep progression). Those are
 * unrelated rates measured in different units that do not sum to a meaningful whole; drawing
 * them as a pie would invent a total that does not exist. Those are shown as separate
 * progress rings instead — see [GoalProgress].
 */
data class MuscleVolumeShare(
    val muscle: MuscleGroup,
    val volumeKg: Double,
    val sharePercent: Double,
    val setCount: Int,
)

object VolumeComposition {
    /**
     * Volume share by muscle group. [muscleOf] resolves an exercise id to its primary muscle,
     * so this stays independent of the persistence layer.
     */
    fun byMuscleGroup(
        sessions: List<WorkoutSession>,
        muscleOf: (String) -> MuscleGroup?,
    ): List<MuscleVolumeShare> {
        val volume = mutableMapOf<MuscleGroup, Double>()
        val sets = mutableMapOf<MuscleGroup, Int>()
        sessions.flatMap { it.performedExercises }.forEach { pe ->
            val muscle = muscleOf(pe.exerciseId) ?: MuscleGroup.OTHER
            volume[muscle] = (volume[muscle] ?: 0.0) + pe.volumeKg
            sets[muscle] = (sets[muscle] ?: 0) + pe.actualSetCount
        }
        val total = volume.values.sum()
        return volume.entries
            .filter { it.value > 0.0 }
            .map { (muscle, vol) ->
                MuscleVolumeShare(
                    muscle = muscle,
                    volumeKg = vol,
                    sharePercent = if (total > 0.0) vol / total * 100.0 else 0.0,
                    setCount = sets[muscle] ?: 0,
                )
            }
            .sortedByDescending { it.volumeKg }
    }
}

/**
 * Progress toward a single named target, expressed as a completion fraction.
 *
 * Shown as an individual ring, never combined into a pie: two goals at 50% do not make
 * a whole, and presenting them as slices of one circle would imply they do.
 */
data class GoalProgress(
    val label: String,
    val startValue: Double,
    val currentValue: Double,
    val targetValue: Double,
    val unit: String,
) {
    /**
     * Fraction of the journey completed, clamped to 0..1. Works for targets that go up
     * (a lift) and targets that go down (bodyweight, waist) because it measures distance
     * travelled relative to distance required.
     */
    val fraction: Double
        get() {
            val required = targetValue - startValue
            if (required == 0.0) return 1.0
            val travelled = currentValue - startValue
            return (travelled / required).coerceIn(0.0, 1.0)
        }

    val percent: Double get() = fraction * 100.0
    val remaining: Double get() = targetValue - currentValue
}
