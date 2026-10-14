package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The v4.1 programme reaching a device that already holds training.
 *
 * This is the case that destroys data if it is got wrong: the seed is never read again once a
 * save file exists, so a new plan has to be installed on top of real history. Every assertion
 * here exists to prove the upgrade adds and never takes away.
 */
class MigrationTest {

    /** A device in the state the user's actually is: old plan, real sessions, own movements. */
    private fun deviceInUse(): AppData {
        val custom = Exercise(
            id = "user-added-1",
            name = "Weighted Leg Raises",
            muscle = Muscle.CORE,
            detail = "Lower core",
            equipment = Equipment.DUMBBELL,
            custom = true,
        )
        val history = listOf(
            finishedSession(
                LocalDate.of(2026, 9, 5),
                listOf(sessionExercise("close-grip-bench", listOf(set(55.0, 8), set(50.0, 10)))),
                title = "Arm Day",
            ),
            finishedSession(
                LocalDate.of(2026, 9, 8),
                listOf(sessionExercise("incline-press", listOf(set(65.0, 6)))),
                title = "Heavy Push + Quads",
            ),
        )
        return Seed.initial(LocalDate.of(2026, 9, 5)).copy(
            exercises = Seed.catalogue + custom,
            programmes = listOf(Seed.programme),
            sessions = history,
            body = listOf(BodyEntry(date = LocalDate.of(2026, 9, 5), weightKg = 80.0, bodyFatPct = 24.0)),
            appliedMigrations = emptyList(),
        )
    }

    private val before = deviceInUse()
    private val after = Migrations.apply(before)

    // -- nothing is lost -----------------------------------------------------------------------

    @Test
    fun `every session survives exactly as it was`() {
        assertEquals(before.sessions.size, after.sessions.size)
        assertEquals(before.sessions.map { it.id }, after.sessions.map { it.id })
        assertEquals(
            before.sessions.sumOf { it.volumeKg },
            after.sessions.sumOf { it.volumeKg },
            0.0,
        )
        assertEquals(before.sessions.sumOf { it.totalReps }, after.sessions.sumOf { it.totalReps })
        assertEquals(
            before.sessions.flatMap { s -> s.exercises.flatMap { it.sets.map { st -> st.weightKg } } },
            after.sessions.flatMap { s -> s.exercises.flatMap { it.sets.map { st -> st.weightKg } } },
        )
    }

    @Test
    fun `a movement the user added themselves is untouched`() {
        val mine = after.exercises.first { it.id == "user-added-1" }
        assertEquals("Weighted Leg Raises", mine.name)
        assertTrue(mine.custom)
        assertFalse(mine.archived)
    }

    @Test
    fun `no movement is ever removed or rewritten`() {
        before.exercises.forEach { original ->
            val now = after.exercises.firstOrNull { it.id == original.id }
            assertNotNull("${original.id} was dropped", now)
            assertEquals(original, now)
        }
    }

    @Test
    fun `body check-ins, goals and preferences are left alone`() {
        assertEquals(before.body, after.body)
        assertEquals(before.goals, after.goals)
        assertEquals(before.prefs, after.prefs)
    }

    @Test
    fun `a workout in progress is not disturbed`() {
        val mid = Actions.startSession(before, "routine-arms", LocalDate.of(2026, 10, 14), 1L)
        val migrated = Migrations.apply(mid)
        assertNotNull(migrated.activeSession)
        assertEquals(mid.activeSession!!.id, migrated.activeSession!!.id)
        assertEquals(mid.activeSession!!.exercises.size, migrated.activeSession!!.exercises.size)
    }

    // -- the new plan arrives ------------------------------------------------------------------

    @Test
    fun `the new programme is installed and is the one in use`() {
        val active = after.activeProgramme!!
        assertEquals(Programme41.ID, active.id)
        assertEquals("Winter Arc v4.1", active.name)
        assertEquals(LocalDate.of(2026, 10, 19), active.startDate)
    }

    @Test
    fun `the old programme is retired rather than deleted, so history keeps its provenance`() {
        val old = after.programmes.first { it.id == "programme-winter-arc" }
        assertFalse("It must no longer be in use", old.active)
        assertEquals(6, old.routines.size)
        // A session performed under it still resolves the routine it was performed under.
        assertNotNull(after.routine("routine-arms"))
    }

    @Test
    fun `exactly one programme is active`() {
        assertEquals(1, after.programmes.count { it.active })
    }

    @Test
    fun `the movements the new plan introduces are added`() {
        listOf(
            "incline-db-press", "low-high-cable-fly", "underhand-lat-pulldown",
            "reverse-pec-deck", "ez-bar-curl", "wide-grip-pushdown",
        ).forEach { id ->
            assertNotNull("$id missing", after.exercise(id))
        }
    }

    @Test
    fun `movements the old plan already had are reused, not duplicated`() {
        // Reuse is what carries history, records and the last-time prefill across the change.
        val ids = after.exercises.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(1, ids.count { it == "close-grip-bench" })
        assertEquals(1, ids.count { it == "single-arm-row" })
    }

    // -- it runs once --------------------------------------------------------------------------

    @Test
    fun `applying it twice changes nothing`() {
        assertEquals(after, Migrations.apply(after))
    }

    @Test
    fun `it does not override a later choice of programme`() {
        // Having run, it must never reach in again and re-activate itself.
        val switched = Actions.activateProgramme(after, "programme-winter-arc")
        val reapplied = Migrations.apply(switched)
        assertEquals("programme-winter-arc", reapplied.activeProgramme!!.id)
    }

    @Test
    fun `a fresh install arrives already on the new plan`() {
        val fresh = Seed.initial(LocalDate.of(2026, 10, 14))
        assertEquals(Programme41.ID, fresh.activeProgramme!!.id)
        assertEquals(fresh, Migrations.apply(fresh))
    }
}

/**
 * The transcription itself, checked against the sheet.
 *
 * The plan states its own totals, which makes them usable as an independent check: if a set,
 * a day or a movement was mistyped, these stop matching.
 */
class Programme41Test {

    private val programme = Programme41.programme
    private val catalogue = (Seed.catalogue + Programme41.newExercises).associateBy { it.id }

    @Test
    fun `the week is the six days the sheet lists, and Sunday is rest`() {
        listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        ).forEach { assertNotNull("No routine for $it", programme.routineFor(it)) }
        assertNull(programme.routineFor(DayOfWeek.SUNDAY))
    }

    @Test
    fun `each day carries the number of sets the sheet states`() {
        fun setsOn(day: DayOfWeek) = programme.routineFor(day)!!.totalSets
        assertEquals(9, setsOn(DayOfWeek.MONDAY))
        assertEquals(19, setsOn(DayOfWeek.TUESDAY))
        assertEquals(8, setsOn(DayOfWeek.WEDNESDAY))
        assertEquals(20, setsOn(DayOfWeek.THURSDAY))
        assertEquals(9, setsOn(DayOfWeek.FRIDAY))
        assertEquals(19, setsOn(DayOfWeek.SATURDAY))
    }

    @Test
    fun `the week totals the 84 sets the sheet claims`() {
        assertEquals(84, programme.routines.sumOf { it.totalSets })
    }

    @Test
    fun `heavy days stay within 20 sets and MMA days within 9`() {
        val heavy = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        val mma = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        heavy.forEach { assertTrue("$it over 20", programme.routineFor(it)!!.totalSets <= 20) }
        mma.forEach { assertTrue("$it over 9", programme.routineFor(it)!!.totalSets <= 9) }
    }

    @Test
    fun `every prescribed movement exists`() {
        val missing = programme.routines
            .flatMap { it.items }
            .map { it.exerciseId }
            .filterNot { it in catalogue }
        assertTrue("Unknown movements: $missing", missing.isEmpty())
    }

    @Test
    fun `every movement carries a rep range, a cue and at least one set`() {
        programme.routines.flatMap { it.items }.forEach { item ->
            assertTrue("${item.exerciseId}: reps reversed", item.repLow <= item.repHigh)
            assertTrue("${item.exerciseId}: no sets", item.sets > 0)
            assertTrue("${item.exerciseId}: no cue", item.cue.isNotBlank())
        }
    }

    @Test
    fun `no rest means go straight into a partner, and the partner rests`() {
        // Zero rest is how a superset is written down: it is an instruction to move on
        // immediately, so it is only ever valid when something follows that does rest. Two
        // movements sharing a letter are not necessarily supersetted -- Tuesday's two chest
        // movements share B and each rest 90s -- so the letter is not the test; the zero is.
        programme.routines.forEach { routine ->
            routine.items.forEachIndexed { index, item ->
                if (item.restSeconds != 0) return@forEachIndexed
                val next = routine.items.getOrNull(index + 1)
                assertNotNull(
                    "${routine.name}: ${item.group} has no rest and nothing follows it",
                    next,
                )
                assertEquals(
                    "${routine.name}: ${item.group} supersets into a different block",
                    item.groupLetter,
                    next!!.groupLetter,
                )
                assertTrue(
                    "${routine.name}: ${next.group} must rest after the pair",
                    next.restSeconds > 0,
                )
            }
        }
    }

    @Test
    fun `every movement that is not supersetted rests on its own`() {
        val resting = programme.routines.flatMap { it.items }.count { it.restSeconds > 0 }
        val supersetted = programme.routines.flatMap { it.items }.count { it.restSeconds == 0 }
        assertEquals(29, resting + supersetted)
        // One pair each on Mon/Tue/Wed/Thu/Fri and three on arm day.
        assertEquals(8, supersetted)
    }

    @Test
    fun `arm day is marked as priority work throughout`() {
        val arms = programme.routineFor(DayOfWeek.SATURDAY)!!
        assertTrue(arms.items.all { it.priority == Priority.ARMS })
        assertEquals(Accent.GOLD, arms.accent)
    }

    @Test
    fun `back work is marked on the days it is dosed`() {
        val backMarked = programme.routines
            .flatMap { it.items }
            .filter { it.priority == Priority.BACK }
            .map { it.exerciseId }
            .toSet()
        assertTrue("single-arm-row" in backMarked)      // Monday, dose #1
        assertTrue("pull-up" in backMarked)             // Thursday
        assertTrue("t-bar-row" in backMarked)
        assertTrue("underhand-lat-pulldown" in backMarked)
    }

    @Test
    fun `start loads are carried so a new movement pre-fills sensibly`() {
        fun load(day: DayOfWeek, exerciseId: String) =
            programme.routineFor(day)!!.items.first { it.exerciseId == exerciseId }.targetWeightKg

        assertEquals(25.0, load(DayOfWeek.TUESDAY, "incline-db-press")!!, 0.001)
        assertEquals(45.0, load(DayOfWeek.THURSDAY, "t-bar-row")!!, 0.001)
        assertEquals(50.0, load(DayOfWeek.SATURDAY, "close-grip-bench")!!, 0.001)
        assertEquals(12.5, load(DayOfWeek.SATURDAY, "hammer-curl")!!, 0.001)
        // "Find your 15RM" is not a number, so nothing is invented.
        assertNull(load(DayOfWeek.MONDAY, "calf-raise"))
    }

    @Test
    fun `a session started from the new plan snapshots it correctly`() {
        val data = Migrations.apply(Seed.initial(LocalDate.of(2026, 10, 14)))
        val started = Actions.startSession(data, "v41-sat", LocalDate.of(2026, 10, 24), 1L)
        val session = started.activeSession!!

        assertEquals("Arm Day", session.title)
        assertEquals(7, session.exercises.size)
        val closeGrip = session.exercises.first()
        assertEquals("close-grip-bench", closeGrip.exerciseId)
        assertEquals(3, closeGrip.plannedSets)
        assertEquals(6, closeGrip.repLow)
        assertEquals(8, closeGrip.repHigh)
        assertEquals(Priority.ARMS, closeGrip.priority)
    }
}
