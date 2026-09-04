package com.winterarc.domain

import com.google.common.truth.Truth.assertThat
import com.winterarc.domain.analytics.*
import com.winterarc.domain.model.MuscleGroup
import org.junit.Test
import java.time.LocalDate

class OneRepMaxTest {

    @Test
    fun `epley matches the figures already in the source workbook`() {
        // These are the exact rows from the workbook's PR tracker, so numbers carried
        // over from it stay comparable.
        assertThat(OneRepMax.epley(90.0, 6).round1()).isEqualTo(108.0)
        assertThat(OneRepMax.epley(70.0, 10).round1()).isEqualTo(93.3)
        assertThat(OneRepMax.epley(17.5, 8).round1()).isEqualTo(22.2)
        assertThat(OneRepMax.epley(40.0, 8).round1()).isEqualTo(50.7)
        assertThat(OneRepMax.epley(20.0, 8).round1()).isEqualTo(25.3)
        assertThat(OneRepMax.epley(55.0, 10).round1()).isEqualTo(73.3)
    }

    @Test
    fun `a single rep returns the weight itself rather than an inflated estimate`() {
        // Naive Epley would return 103.33 here, claiming a 1RM 3% above a lift already
        // performed for one rep.
        assertThat(OneRepMax.epley(100.0, 1)).isEqualTo(100.0)
    }

    @Test
    fun `non-positive input yields zero rather than a misleading number`() {
        assertThat(OneRepMax.epley(100.0, 0)).isEqualTo(0.0)
        assertThat(OneRepMax.epley(0.0, 10)).isEqualTo(0.0)
        assertThat(OneRepMax.epley(-10.0, 5)).isEqualTo(0.0)
    }

    @Test
    fun `reliability flag marks high-rep estimates as unreliable`() {
        assertThat(OneRepMax.isReliable(8)).isTrue()
        assertThat(OneRepMax.isReliable(12)).isTrue()
        assertThat(OneRepMax.isReliable(20)).isFalse()
        assertThat(OneRepMax.isReliable(0)).isFalse()
    }
}

class PrDetectorTest {

    private val bench = "ex-bench"
    private val d1 = LocalDate.of(2026, 9, 1)
    private val d2 = LocalDate.of(2026, 9, 8)
    private val d3 = LocalDate.of(2026, 9, 15)

    @Test
    fun `a first ever performance records PRs with no previous value`() {
        val s1 = completedSession("s1", d1, bench, listOf(70.0 to 10))
        val prs = PrDetector.detect(bench, s1, emptyList())

        // No rep PR: with no prior set at 70 kg there is nothing to have beaten.
        assertThat(prs.map { it.type }).containsExactly(
            PrType.WEIGHT, PrType.ESTIMATED_1RM, PrType.SESSION_VOLUME,
        )
        assertThat(prs.all { it.isFirstEver }).isTrue()
    }

    @Test
    fun `a heavier top set is a weight PR`() {
        val s1 = completedSession("s1", d1, bench, listOf(70.0 to 10))
        val s2 = completedSession("s2", d2, bench, listOf(80.0 to 8))

        val prs = PrDetector.detect(bench, s2, listOf(s1))
        val weightPr = prs.single { it.type == PrType.WEIGHT }

        assertThat(weightPr.value).isEqualTo(80.0)
        assertThat(weightPr.previousValue).isEqualTo(70.0)
        assertThat(weightPr.improvement).isEqualTo(10.0)
    }

    @Test
    fun `repeating a previous best generates no PR`() {
        val s1 = completedSession("s1", d1, bench, listOf(70.0 to 10))
        val s2 = completedSession("s2", d2, bench, listOf(70.0 to 10))

        assertThat(PrDetector.detect(bench, s2, listOf(s1))).isEmpty()
    }

    /**
     * The rule that stops the PR feed filling with noise: a light high-rep set must not
     * register as a rep record against a heavy low-rep one.
     */
    @Test
    fun `a lighter high-rep set is not a rep PR over a heavier low-rep set`() {
        val heavy = completedSession("s1", d1, bench, listOf(100.0 to 5))
        val light = completedSession("s2", d2, bench, listOf(40.0 to 20))

        val prs = PrDetector.detect(bench, light, listOf(heavy))

        assertThat(prs.none { it.type == PrType.REPS }).isTrue()
        assertThat(prs.none { it.type == PrType.WEIGHT }).isTrue()
        // 40 x 20 is a lighter session in every dimension that matters.
        assertThat(prs.none { it.type == PrType.ESTIMATED_1RM }).isTrue()
    }

    /**
     * Bodyweight progression: every pull-up set sits at the same load, so reps are the
     * only progression signal and MUST register.
     */
    @Test
    fun `bodyweight reps progression registers as a rep PR`() {
        val s1 = completedSession("s1", d1, "ex-pullup", listOf(0.0 to 8, 0.0 to 8))
        val s2 = completedSession("s2", d2, "ex-pullup", listOf(0.0 to 10, 0.0 to 9))

        val repPr = PrDetector.detect("ex-pullup", s2, listOf(s1)).single { it.type == PrType.REPS }

        assertThat(repPr.value).isEqualTo(10.0)
        assertThat(repPr.previousValue).isEqualTo(8.0)
    }

    @Test
    fun `more reps at the same weight is a rep PR`() {
        val s1 = completedSession("s1", d1, bench, listOf(80.0 to 8))
        val s2 = completedSession("s2", d2, bench, listOf(80.0 to 10))

        val repPr = PrDetector.detect(bench, s2, listOf(s1)).single { it.type == PrType.REPS }

        assertThat(repPr.value).isEqualTo(10.0)
        assertThat(repPr.previousValue).isEqualTo(8.0)
        assertThat(repPr.weightKg).isEqualTo(80.0)
    }

    @Test
    fun `fewer reps at a heavier weight is a weight PR but not a rep PR`() {
        val s1 = completedSession("s1", d1, bench, listOf(80.0 to 10))
        val s2 = completedSession("s2", d2, bench, listOf(90.0 to 6))

        val prs = PrDetector.detect(bench, s2, listOf(s1))

        assertThat(prs.any { it.type == PrType.WEIGHT }).isTrue()
        assertThat(prs.none { it.type == PrType.REPS }).isTrue()
    }

    @Test
    fun `extra sets count toward the session volume PR`() {
        val s1 = completedSession("s1", d1, bench, listOf(70.0 to 10, 70.0 to 10, 70.0 to 10))
        // Same weights, one additional set — volume rises purely because of the extra set.
        val s2 = completedSession("s2", d2, bench, listOf(70.0 to 10, 70.0 to 10, 70.0 to 10, 70.0 to 10))

        val volPr = PrDetector.detect(bench, s2, listOf(s1)).single { it.type == PrType.SESSION_VOLUME }

        assertThat(volPr.value).isEqualTo(2800.0)
        assertThat(volPr.previousValue).isEqualTo(2100.0)
    }

    @Test
    fun `a session never counts as its own history`() {
        val s1 = completedSession("s1", d1, bench, listOf(70.0 to 10))
        // Passing the candidate inside the history list must not suppress its own PRs.
        val prs = PrDetector.detect(bench, s1, listOf(s1))

        assertThat(prs).isNotEmpty()
    }

    @Test
    fun `later sessions do not count as history for an earlier one`() {
        val early = completedSession("s1", d1, bench, listOf(70.0 to 10))
        val later = completedSession("s2", d3, bench, listOf(120.0 to 10))

        // Re-evaluating the earlier session must not be spoiled by a heavier future session.
        val prs = PrDetector.detect(bench, early, listOf(later))

        assertThat(prs.any { it.type == PrType.WEIGHT }).isTrue()
    }

    @Test
    fun `warm-up sets never generate PRs`() {
        val s1 = completedSession("s1", d1, bench, listOf(60.0 to 10))
        val heavyWarmup = completedSession("s2", d2, bench, listOf(200.0 to 1)).let { s ->
            s.copy(
                exercises = s.exercises.map { pe ->
                    pe.copy(sets = pe.sets.map { it.copy(isWarmup = true) })
                },
            )
        }

        assertThat(PrDetector.detect(bench, heavyWarmup, listOf(s1))).isEmpty()
    }

    @Test
    fun `detectAll covers every exercise in the session`() {
        val s = completedSession("s1", d1, bench, listOf(70.0 to 10))
        val prs = PrDetector.detectAll(s, emptyList())

        assertThat(prs.map { it.exerciseId }.distinct()).containsExactly(bench)
    }
}

class VolumeTest {

    private val bench = "ex-bench"

    @Test
    fun `volume is weight times reps summed over working sets`() {
        val s = completedSession("s1", LocalDate.of(2026, 9, 1), bench, listOf(70.0 to 10, 80.0 to 8))
        assertThat(Volume.ofSession(s)).isEqualTo(700.0 + 640.0)
    }

    @Test
    fun `weekly aggregation groups by the monday of the iso week`() {
        // 2026-09-01 is a Tuesday; 2026-09-05 a Saturday. Same week -> one bucket.
        val a = completedSession("a", LocalDate.of(2026, 9, 1), bench, listOf(100.0 to 10))
        val b = completedSession("b", LocalDate.of(2026, 9, 5), bench, listOf(100.0 to 10))
        val c = completedSession("c", LocalDate.of(2026, 9, 8), bench, listOf(100.0 to 5))

        val byWeek = Volume.byWeek(listOf(a, b, c))

        assertThat(byWeek).hasSize(2)
        assertThat(byWeek[LocalDate.of(2026, 8, 31)]).isEqualTo(2000.0)
        assertThat(byWeek[LocalDate.of(2026, 9, 7)]).isEqualTo(500.0)
    }

    @Test
    fun `monthly aggregation groups by first of month`() {
        val a = completedSession("a", LocalDate.of(2026, 9, 30), bench, listOf(100.0 to 10))
        val b = completedSession("b", LocalDate.of(2026, 10, 1), bench, listOf(100.0 to 10))

        val byMonth = Volume.byMonth(listOf(a, b))

        assertThat(byMonth.keys).containsExactly(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
        ).inOrder()
    }
}

class ConsistencyTest {

    private val bench = "ex-bench"
    private fun s(id: String, date: LocalDate) = completedSession(id, date, bench, listOf(50.0 to 10))

    @Test
    fun `a rest day does not break the streak`() {
        // Six training days then Sunday off, three weeks running.
        val sessions = listOf(
            s("a", LocalDate.of(2026, 8, 25)),
            s("b", LocalDate.of(2026, 9, 1)),
            s("c", LocalDate.of(2026, 9, 8)),
        )
        val streak = Consistency.currentWeekStreak(sessions, today = LocalDate.of(2026, 9, 10))

        assertThat(streak).isEqualTo(3)
    }

    @Test
    fun `an empty current week does not break a streak that ran until last week`() {
        val sessions = listOf(
            s("a", LocalDate.of(2026, 8, 25)),
            s("b", LocalDate.of(2026, 9, 1)),
        )
        // Week of 7 Sept has no sessions yet, but the week is not over.
        val streak = Consistency.currentWeekStreak(sessions, today = LocalDate.of(2026, 9, 10))

        assertThat(streak).isEqualTo(2)
    }

    @Test
    fun `a fully missed week breaks the streak`() {
        val sessions = listOf(
            s("a", LocalDate.of(2026, 8, 18)),
            // week of 24 Aug missed entirely
            s("b", LocalDate.of(2026, 9, 1)),
        )
        val streak = Consistency.currentWeekStreak(sessions, today = LocalDate.of(2026, 9, 3))

        assertThat(streak).isEqualTo(1)
    }

    @Test
    fun `no sessions means no streak`() {
        assertThat(Consistency.currentWeekStreak(emptyList(), LocalDate.of(2026, 9, 10))).isEqualTo(0)
    }

    @Test
    fun `longest streak is found across the whole history`() {
        val sessions = listOf(
            s("a", LocalDate.of(2026, 6, 1)),
            s("b", LocalDate.of(2026, 6, 8)),
            s("c", LocalDate.of(2026, 6, 15)),
            // gap
            s("d", LocalDate.of(2026, 8, 3)),
        )
        assertThat(Consistency.longestWeekStreak(sessions)).isEqualTo(3)
    }

    @Test
    fun `average sessions per week divides by the window not by weeks trained`() {
        val sessions = listOf(
            s("a", LocalDate.of(2026, 9, 1)),
            s("b", LocalDate.of(2026, 9, 3)),
            s("c", LocalDate.of(2026, 9, 5)),
        )
        // 3 sessions across a 4-week window.
        val avg = Consistency.averageSessionsPerWeek(sessions, LocalDate.of(2026, 9, 10), weeks = 4)

        assertThat(avg).isEqualTo(0.75)
    }
}

class VolumeCompositionTest {

    @Test
    fun `muscle group shares sum to one hundred percent`() {
        val chest = completedSession("a", LocalDate.of(2026, 9, 1), "ex-bench", listOf(100.0 to 10))
        val back = completedSession("b", LocalDate.of(2026, 9, 2), "ex-row", listOf(50.0 to 10))

        val shares = VolumeComposition.byMuscleGroup(listOf(chest, back)) { id ->
            if (id == "ex-bench") MuscleGroup.CHEST else MuscleGroup.BACK
        }

        // Chest 1000 kg, back 500 kg, total 1500 kg.
        assertThat(shares.sumOf { it.sharePercent }).isWithin(0.0001).of(100.0)
        assertThat(shares.first().muscle).isEqualTo(MuscleGroup.CHEST)
        assertThat(shares.first().volumeKg).isEqualTo(1000.0)
        assertThat(shares.first().sharePercent).isWithin(0.01).of(66.67)
        assertThat(shares.last().sharePercent).isWithin(0.01).of(33.33)
    }

    @Test
    fun `an unknown exercise falls into OTHER rather than being dropped`() {
        val s = completedSession("a", LocalDate.of(2026, 9, 1), "ex-mystery", listOf(100.0 to 10))
        val shares = VolumeComposition.byMuscleGroup(listOf(s)) { null }

        assertThat(shares.single().muscle).isEqualTo(MuscleGroup.OTHER)
        assertThat(shares.single().sharePercent).isEqualTo(100.0)
    }
}

class GoalProgressTest {

    @Test
    fun `progress toward a rising target`() {
        val g = GoalProgress("DB Curl", startValue = 17.5, currentValue = 20.0, targetValue = 25.0, unit = "kg")
        assertThat(g.fraction).isWithin(0.0001).of(2.5 / 7.5)
    }

    @Test
    fun `progress toward a falling target such as bodyweight`() {
        val g = GoalProgress("Weight", startValue = 80.5, currentValue = 78.0, targetValue = 73.0, unit = "kg")
        assertThat(g.fraction).isWithin(0.0001).of(2.5 / 7.5)
    }

    @Test
    fun `moving away from the target clamps at zero rather than reporting negative progress`() {
        val g = GoalProgress("Weight", startValue = 80.5, currentValue = 82.0, targetValue = 73.0, unit = "kg")
        assertThat(g.fraction).isEqualTo(0.0)
    }

    @Test
    fun `overshooting the target clamps at one hundred percent`() {
        val g = GoalProgress("Weight", startValue = 80.5, currentValue = 70.0, targetValue = 73.0, unit = "kg")
        assertThat(g.percent).isEqualTo(100.0)
    }
}
