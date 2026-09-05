package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The seeded session is a real workout, exported from the app as CSV and read back in here.
 * These assertions are the import check: if any figure drifts from what was actually performed,
 * the build fails rather than quietly shipping altered history.
 */
class SeededSessionTest {

    private val data = Seed.initial(LocalDate.of(2026, 9, 5))
    private val session = data.sessions.single()

    @Test
    fun `the recorded session totals match the exported figures exactly`() {
        assertEquals(5627.5, session.volumeKg, 0.001)   // dashboard read 5627.5 kg
        assertEquals(218, session.totalReps)            // dashboard read 218 reps
        assertEquals(25, session.workingSetCount)       // dashboard read 25 sets
        assertEquals(121, session.durationMinutes)      // dashboard read 2h 1m
    }

    @Test
    fun `every movement performed is present with the right number of sets`() {
        val counts = session.exercises.associate { it.exerciseId to it.sets.size }
        assertEquals(
            mapOf(
                "close-grip-bench" to 5,
                "overhead-extension" to 4,
                "pushdown" to 3,
                "standing-db-curl" to 4,
                "incline-db-curl" to 5,
                "preacher-curl" to 2,
                "skull-crusher" to 2,
            ),
            counts,
        )
    }

    @Test
    fun `the heaviest set was fifty five kilograms`() {
        val heaviest = session.exercises.flatMap { it.workingSets }.maxOf { it.weightKg }
        assertEquals(55.0, heaviest, 0.001)
    }

    @Test
    fun `half kilogram loads survive the seed`() {
        val curl = session.exercises.first { it.exerciseId == "standing-db-curl" }
        assertEquals(17.5, curl.sets.first().weightKg, 0.0)
        val incline = session.exercises.first { it.exerciseId == "incline-db-curl" }
        assertEquals(12.5, incline.sets.first().weightKg, 0.0)
        assertEquals(7.5, incline.sets.last().weightKg, 0.0)
    }

    @Test
    fun `sets performed beyond the prescription are recorded as extra`() {
        val closeGrip = session.exercises.first { it.exerciseId == "close-grip-bench" }
        assertEquals(4, closeGrip.plannedSets)
        assertEquals(1, closeGrip.extraSets)

        val incline = session.exercises.first { it.exerciseId == "incline-db-curl" }
        assertEquals(3, incline.plannedSets)
        assertEquals(2, incline.extraSets)
    }

    @Test
    fun `every seeded movement exists in the catalogue`() {
        session.exercises.forEach {
            assertTrue("${it.exerciseId} missing", data.exercise(it.exerciseId) != null)
        }
    }

    @Test
    fun `the seeded session round-trips through the export it came from`() {
        val csv = CsvExport.sets(data).trim().lines()
        assertEquals(26, csv.size)                       // header plus 25 sets
        assertTrue(csv[1].contains("Close-Grip Bench Press"))
        assertTrue(csv[1].contains("40"))
        assertTrue(csv.any { it.contains("Skull Crusher") })
    }
}

class SessionBreakdownTest {

    private val data = Seed.initial(LocalDate.of(2026, 9, 5))
    private val breakdown = Analytics.sessionBreakdown(
        data.sessions.single(),
        data.sessions,
        data.exerciseById,
    )

    @Test
    fun `the first set of a movement has no direction to compare against`() {
        val closeGrip = breakdown.exercises.first { it.exerciseId == "close-grip-bench" }
        assertEquals(SetDirection.FIRST, closeGrip.sets.first().direction)
    }

    @Test
    fun `climbing load reads as up and dropping load reads as down`() {
        // 40x15 -> 50x10 -> 55x8 -> 55x7 -> 40x8
        val closeGrip = breakdown.exercises.first { it.exerciseId == "close-grip-bench" }
        assertEquals(
            listOf(
                SetDirection.FIRST,
                SetDirection.UP,     // 40 -> 50
                SetDirection.UP,     // 50 -> 55
                SetDirection.DOWN,   // same 55, fewer reps
                SetDirection.DOWN,   // 55 -> 40
            ),
            closeGrip.sets.map { it.direction },
        )
    }

    @Test
    fun `the same load for the same reps is held rather than called progress`() {
        // 55x10 -> 50x10 -> 50x10
        val pushdown = breakdown.exercises.first { it.exerciseId == "pushdown" }
        assertEquals(SetDirection.DOWN, pushdown.sets[1].direction)
        assertEquals(SetDirection.HELD, pushdown.sets[2].direction)
    }

    @Test
    fun `only one set is marked as the top set even when the load repeats`() {
        val closeGrip = breakdown.exercises.first { it.exerciseId == "close-grip-bench" }
        assertEquals(1, closeGrip.sets.count { it.isTop })
        assertEquals(55.0, closeGrip.sets.first { it.isTop }.weightKg, 0.001)
        assertEquals(3, closeGrip.sets.first { it.isTop }.index)
    }

    @Test
    fun `the drop from the top set to the last is reported`() {
        // top 55, finished on 40
        val closeGrip = breakdown.exercises.first { it.exerciseId == "close-grip-bench" }
        assertEquals(15.0, closeGrip.droppedBy, 0.001)
    }

    @Test
    fun `a first ever performance has nothing to compare against`() {
        assertTrue(breakdown.exercises.all { it.vsPreviousKg == null })
    }

    @Test
    fun `a repeat performance reports the change in top set`() {
        val second = finishedSession(
            LocalDate.of(2026, 9, 12),
            listOf(sessionExercise("close-grip-bench", listOf(set(60.0, 6)))),
            title = "Arm Day",
        )
        val result = Analytics.sessionBreakdown(
            second,
            data.sessions + second,
            data.exerciseById,
        )
        assertEquals(5.0, result.exercises.single().vsPreviousKg!!, 0.001)
    }

    @Test
    fun `the breakdown carries the session totals`() {
        assertEquals(5627.5, breakdown.volumeKg, 0.001)
        assertEquals(25, breakdown.setCount)
        assertEquals(218, breakdown.repCount)
        assertEquals(7, breakdown.exercises.size)
    }

    @Test
    fun `warm-ups are shown in the ladder but never counted as the top set`() {
        val warmed = finishedSession(
            LocalDate.of(2026, 9, 12),
            listOf(
                sessionExercise(
                    "close-grip-bench",
                    listOf(set(100.0, 5, warmup = true), set(60.0, 6)),
                ),
            ),
        )
        val ex = Analytics.sessionBreakdown(warmed, listOf(warmed), data.exerciseById).exercises.single()
        assertEquals(2, ex.sets.size)
        assertFalse("A warm-up must never be the top set", ex.sets.first { it.warmup }.isTop)
        assertEquals(60.0, ex.topWeightKg, 0.001)
    }

    @Test
    fun `a session with nothing logged breaks down to nothing rather than failing`() {
        val empty = finishedSession(LocalDate.of(2026, 9, 12), emptyList())
        val result = Analytics.sessionBreakdown(empty, listOf(empty), data.exerciseById)
        assertTrue(result.exercises.isEmpty())
        assertEquals(0.0, result.volumeKg, 0.001)
        assertNull(result.exercises.firstOrNull()?.vsPreviousKg)
    }
}
