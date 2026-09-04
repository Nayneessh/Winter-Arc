package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {

    // -- estimated 1RM -----------------------------------------------------------------------

    @Test
    fun `a single rep estimates as the weight itself`() {
        assertEquals(100.0, Analytics.e1rm(100.0, 1), 0.0001)
    }

    @Test
    fun `Epley adds one thirtieth of the load per rep`() {
        // 100 x 10 -> 100 * (1 + 10/30) = 133.33
        assertEquals(133.333, Analytics.e1rm(100.0, 10), 0.01)
    }

    @Test
    fun `an unperformed set has no estimated max`() {
        assertEquals(0.0, Analytics.e1rm(100.0, 0), 0.0001)
        assertEquals(0.0, Analytics.e1rm(0.0, 5), 0.0001)
    }

    @Test
    fun `the estimate is flagged unreliable above twelve reps`() {
        assertTrue(Analytics.e1rmReliable(12))
        assertFalse(Analytics.e1rmReliable(13))
        assertFalse(Analytics.e1rmReliable(0))
    }

    // -- volume ------------------------------------------------------------------------------

    @Test
    fun `volume is weight times reps over completed working sets only`() {
        val ex = sessionExercise(
            "bench-press",
            listOf(
                set(100.0, 10),                     // 1000
                set(100.0, 8),                      // 800
                set(60.0, 10, warmup = true),       // warm-up, excluded
                set(100.0, 5, done = false),        // not performed, excluded
            ),
        )
        assertEquals(1800.0, ex.volumeKg, 0.001)
        assertEquals(18, ex.totalReps)
        assertEquals(2, ex.workingSets.size)
    }

    @Test
    fun `a zero rep set contributes nothing`() {
        val ex = sessionExercise("bench-press", listOf(set(100.0, 0)))
        assertEquals(0.0, ex.volumeKg, 0.001)
        assertTrue(ex.workingSets.isEmpty())
    }

    @Test
    fun `extra sets are counted beyond the prescription and never go negative`() {
        val over = sessionExercise("bench-press", List(6) { set(100.0, 8) }, plannedSets = 4)
        assertEquals(2, over.extraSets)

        val under = sessionExercise("bench-press", List(2) { set(100.0, 8) }, plannedSets = 4)
        assertEquals(0, under.extraSets)
    }

    // -- weeks -------------------------------------------------------------------------------

    @Test
    fun `the week always starts on Monday regardless of locale`() {
        // 2026-09-05 is a Saturday; 2026-09-06 a Sunday; both belong to the week of Mon 31 Aug.
        assertEquals(LocalDate.of(2026, 8, 31), Analytics.weekStart(LocalDate.of(2026, 9, 5)))
        assertEquals(LocalDate.of(2026, 8, 31), Analytics.weekStart(LocalDate.of(2026, 9, 6)))
        assertEquals(LocalDate.of(2026, 9, 7), Analytics.weekStart(LocalDate.of(2026, 9, 7)))
    }

    @Test
    fun `week buckets cover every week in the window even when empty`() {
        val sessions = listOf(
            finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(100.0, 10))))),
        )
        val buckets = Analytics.weekBuckets(sessions, weeks = 4, today = TODAY)
        assertEquals(4, buckets.size)
        assertEquals(0.0, buckets[0].volumeKg, 0.001)
        assertEquals(1000.0, buckets.last().volumeKg, 0.001)
        assertEquals(1, buckets.last().sessions)
    }

    // -- streaks -----------------------------------------------------------------------------

    @Test
    fun `streaks count consecutive weeks so a prescribed rest day cannot break one`() {
        val sessions = (0..3).map { weeksAgo ->
            finishedSession(
                TODAY.minusWeeks(weeksAgo.toLong()),
                listOf(sessionExercise("bench-press", listOf(set(100.0, 5)))),
            )
        }
        val stats = Analytics.consistency(sessions, TODAY)
        assertEquals(4, stats.weekStreak)
        assertEquals(4, stats.longestWeekStreak)
    }

    @Test
    fun `a streak survives a current week that has not been trained yet`() {
        // Trained last week and the week before, nothing yet this week. The run is intact:
        // this week is not over, so it must not be reported as broken.
        val sessions = listOf(
            finishedSession(TODAY.minusWeeks(1), listOf(sessionExercise("a", listOf(set(50.0, 5))))),
            finishedSession(TODAY.minusWeeks(2), listOf(sessionExercise("a", listOf(set(50.0, 5))))),
        )
        assertEquals(2, Analytics.consistency(sessions, TODAY).weekStreak)
    }

    @Test
    fun `a missed week breaks the current streak but not the record`() {
        val sessions = listOf(
            finishedSession(TODAY, listOf(sessionExercise("a", listOf(set(50.0, 5))))),
            // gap at 1 week ago
            finishedSession(TODAY.minusWeeks(2), listOf(sessionExercise("a", listOf(set(50.0, 5))))),
            finishedSession(TODAY.minusWeeks(3), listOf(sessionExercise("a", listOf(set(50.0, 5))))),
            finishedSession(TODAY.minusWeeks(4), listOf(sessionExercise("a", listOf(set(50.0, 5))))),
        )
        val stats = Analytics.consistency(sessions, TODAY)
        assertEquals(1, stats.weekStreak)
        assertEquals(3, stats.longestWeekStreak)
    }

    // -- muscle split -------------------------------------------------------------------------

    @Test
    fun `volume share by muscle sums to one`() {
        val data = baseData()
        val sessions = listOf(
            finishedSession(
                TODAY,
                listOf(
                    sessionExercise("bench-press", listOf(set(100.0, 10))),   // chest 1000
                    sessionExercise("standing-db-curl", listOf(set(20.0, 10))), // biceps 200
                ),
            ),
        )
        val split = Analytics.muscleVolume(sessions, data.exerciseById)
        assertEquals(1.0, split.sumOf { it.share }, 0.0001)
        assertEquals(Muscle.CHEST, split.first().muscle)
    }

    // -- personal records ---------------------------------------------------------------------

    @Test
    fun `a first ever performance is not a personal record`() {
        val data = baseData()
        val session = finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(100.0, 5)))))
        val prs = Analytics.recordsFor(session, listOf(session), data.exerciseById)
        assertTrue(prs.isEmpty())
    }

    @Test
    fun `beating a previous best is detected and reports what it beat`() {
        val data = baseData()
        val first = finishedSession(TODAY.minusWeeks(1), listOf(sessionExercise("bench-press", listOf(set(100.0, 5)))))
        val second = finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(110.0, 5)))))
        val prs = Analytics.recordsFor(second, listOf(first, second), data.exerciseById)

        val weightPr = prs.first { it.type == PrType.WEIGHT }
        assertEquals(110.0, weightPr.value, 0.001)
        assertEquals(100.0, weightPr.previous!!, 0.001)
    }

    @Test
    fun `equalling a previous best is not a record`() {
        val data = baseData()
        val first = finishedSession(TODAY.minusWeeks(1), listOf(sessionExercise("bench-press", listOf(set(100.0, 5)))))
        val second = finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(100.0, 5)))))
        val prs = Analytics.recordsFor(second, listOf(first, second), data.exerciseById)
        assertTrue(prs.none { it.type == PrType.WEIGHT })
    }

    @Test
    fun `more reps at the same load is a rep record and an estimated max record`() {
        val data = baseData()
        val first = finishedSession(TODAY.minusWeeks(1), listOf(sessionExercise("bench-press", listOf(set(100.0, 5)))))
        val second = finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(100.0, 8)))))
        val prs = Analytics.recordsFor(second, listOf(first, second), data.exerciseById)
        assertTrue(prs.any { it.type == PrType.REPS })
        assertTrue(prs.any { it.type == PrType.E1RM })
    }

    // -- per exercise -------------------------------------------------------------------------

    @Test
    fun `exercise stats track first and latest performance and the best of each measure`() {
        val data = baseData()
        val sessions = listOf(
            finishedSession(TODAY.minusWeeks(2), listOf(sessionExercise("bench-press", listOf(set(80.0, 10))))),
            finishedSession(TODAY.minusWeeks(1), listOf(sessionExercise("bench-press", listOf(set(90.0, 8))))),
            finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(100.0, 6), set(95.0, 8))))),
        )
        val stats = Analytics.exerciseStats("bench-press", sessions, data.exerciseById)!!
        assertEquals(3, stats.timesPerformed)
        assertEquals(80.0, stats.firstTopWeightKg, 0.001)
        assertEquals(100.0, stats.latestTopWeightKg, 0.001)
        assertEquals(20.0, stats.weightGainKg, 0.001)
        assertEquals(100.0, stats.heaviestKg, 0.001)
        assertEquals(10, stats.bestReps)
        assertEquals(3, stats.topWeightSeries.size)
    }

    @Test
    fun `an exercise never performed has no stats`() {
        val data = baseData()
        assertNull(Analytics.exerciseStats("bench-press", emptyList(), data.exerciseById))
    }

    // -- strength index -----------------------------------------------------------------------

    @Test
    fun `the strength index starts at one hundred and rises with the lifts`() {
        val sessions = listOf(
            finishedSession(TODAY.minusWeeks(2), listOf(sessionExercise("bench-press", listOf(set(100.0, 1))))),
            finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(120.0, 1))))),
        )
        val index = Analytics.strengthIndex(sessions)
        assertEquals(100.0, index.first().value, 0.001)
        assertEquals(120.0, index.last().value, 0.001)
    }

    // -- goals --------------------------------------------------------------------------------

    @Test
    fun `a downward goal reports progress as distance travelled`() {
        // 80 kg heading to 73 kg, currently 76.5 -> half way.
        val goal = GoalProgress("Bodyweight", start = 80.0, current = 76.5, target = 73.0)
        assertEquals(0.5, goal.fraction, 0.001)
    }

    @Test
    fun `an upward goal reports progress as distance travelled`() {
        val goal = GoalProgress("DB Curl", start = 17.5, current = 21.25, target = 25.0)
        assertEquals(0.5, goal.fraction, 0.001)
    }

    @Test
    fun `progress never reads below zero or above one`() {
        assertEquals(0.0, GoalProgress("x", 80.0, 85.0, 73.0).fraction, 0.001)
        assertEquals(1.0, GoalProgress("x", 80.0, 70.0, 73.0).fraction, 0.001)
    }

    @Test
    fun `the milestone sits proportionally between start and target`() {
        val goal = GoalProgress("DB Curl", start = 17.5, current = 17.5, target = 25.0, milestone = 21.0)
        assertEquals((21.0 - 17.5) / (25.0 - 17.5), goal.milestoneFraction!!, 0.001)
    }

    // -- body ---------------------------------------------------------------------------------

    @Test
    fun `composition is only computed from entries carrying both weight and body fat`() {
        val entries = listOf(
            BodyEntry(date = TODAY.minusWeeks(4), weightKg = 80.0, bodyFatPct = 24.0),
            BodyEntry(date = TODAY.minusWeeks(2), weightKg = 79.0),                       // no bf
            BodyEntry(date = TODAY, weightKg = 78.0, bodyFatPct = 22.0),
        )
        val stats = Analytics.bodyStats(entries)
        assertEquals(78.0, stats.latestWeightKg!!, 0.001)
        assertEquals(22.0, stats.latestBodyFatPct!!, 0.001)
        // 80 * 0.24 = 19.2 -> 78 * 0.22 = 17.16
        assertEquals(17.16 - 19.2, stats.fatMassChangeKg!!, 0.001)
        assertEquals(3, stats.weightSeries.size)
        assertEquals(2, stats.bodyFatSeries.size)
    }

    @Test
    fun `a single body entry yields no composition change`() {
        val stats = Analytics.bodyStats(listOf(BodyEntry(date = TODAY, weightKg = 80.0, bodyFatPct = 24.0)))
        assertNull(stats.fatMassChangeKg)
        assertEquals(19.2, stats.fatMassKg!!, 0.001)
    }

    @Test
    fun `measurement deltas need two readings of the same site`() {
        val entries = listOf(
            BodyEntry(date = TODAY.minusWeeks(4), measurementsCm = mapOf(MeasurementSites.ARM_LEFT to 36.0)),
            BodyEntry(date = TODAY, measurementsCm = mapOf(
                MeasurementSites.ARM_LEFT to 37.5,
                MeasurementSites.WAIST to 88.0,
            )),
        )
        val deltas = Analytics.bodyStats(entries).measurementDeltas
        assertEquals(1, deltas.size)
        assertEquals(1.5, deltas.single().deltaCm, 0.001)
        assertEquals("Left arm", deltas.single().label)
    }

    // -- the dashboard as a whole --------------------------------------------------------------

    @Test
    fun `the dashboard assembles without data and reports zeroes rather than failing`() {
        val dash = Analytics.dashboard(baseData(), RangeFilter.W12, TODAY)
        assertEquals(0, dash.sessionCount)
        assertEquals(0.0, dash.volumeKg, 0.001)
        assertTrue(dash.muscleVolume.isEmpty())
        // Bodyweight, body fat and the seeded lift goal all still render: a goal with no
        // training behind it sits at zero progress, which is information, not an error.
        assertEquals(3, dash.goalProgress.size)
        assertEquals(0.0, dash.goalProgress.first { it.label == "Bodyweight" }.fraction, 0.001)
    }

    @Test
    fun `the range filter excludes sessions outside the window`() {
        val sessions = listOf(
            finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(100.0, 10))))),
            finishedSession(TODAY.minusDays(200), listOf(sessionExercise("bench-press", listOf(set(50.0, 10))))),
        )
        val dash = Analytics.dashboard(baseData(sessions), RangeFilter.W4, TODAY)
        assertEquals(1, dash.sessionCount)
        assertEquals(1000.0, dash.volumeKg, 0.001)
    }

    @Test
    fun `the trend compares against an equally long preceding window`() {
        val sessions = listOf(
            finishedSession(TODAY, listOf(sessionExercise("a", listOf(set(100.0, 10))))),           // in window
            finishedSession(TODAY.minusDays(35), listOf(sessionExercise("a", listOf(set(50.0, 10))))), // prior window
        )
        val dash = Analytics.dashboard(baseData(sessions), RangeFilter.W4, TODAY)
        assertEquals(1000.0, dash.volumeTrend.current, 0.001)
        assertEquals(500.0, dash.volumeTrend.previous!!, 0.001)
        assertTrue(dash.volumeTrend.rising)
    }
}
