package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionsTest {

    private val now = 1_700_000_000_000L

    // -- starting ------------------------------------------------------------------------------

    @Test
    fun `starting from a routine snapshots the whole prescription`() {
        val data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val session = data.activeSession!!

        assertEquals("Arm Day", session.title)
        assertEquals(6, session.exercises.size)

        val closeGrip = session.exercises.first()
        assertEquals("close-grip-bench", closeGrip.exerciseId)
        assertEquals(4, closeGrip.plannedSets)
        assertEquals(6, closeGrip.repLow)
        assertEquals(8, closeGrip.repHigh)
        assertEquals(150, closeGrip.restSeconds)
        assertEquals(Priority.ARMS, closeGrip.priority)
        assertTrue(closeGrip.cue.isNotBlank())
        assertEquals(4, closeGrip.sets.size)
    }

    @Test
    fun `editing the programme afterwards never rewrites a session already started`() {
        val started = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val original = started.activeSession!!.exercises.first().plannedSets

        val routine = started.routine("routine-arms")!!
        val edited = Actions.updateRoutine(
            started,
            "programme-winter-arc",
            routine.copy(items = routine.items.map { it.copy(sets = 99) }),
        )

        assertEquals(original, edited.activeSession!!.exercises.first().plannedSets)
        assertEquals(99, edited.routine("routine-arms")!!.items.first().sets)
    }

    @Test
    fun `a new session is prefilled from the last time the movement was performed`() {
        val history = finishedSession(
            TODAY.minusWeeks(1),
            listOf(sessionExercise("close-grip-bench", listOf(set(30.0, 8), set(32.5, 7)))),
        )
        val data = Actions.startSession(baseData(listOf(history)), "routine-arms", TODAY, now)

        val closeGrip = data.activeSession!!.exercises.first { it.exerciseId == "close-grip-bench" }
        assertEquals(32.5, closeGrip.sets.first().weightKg, 0.001)
        assertEquals(7, closeGrip.sets.first().reps)
        assertFalse(closeGrip.sets.first().done)
    }

    @Test
    fun `an open session with no routine starts empty and titled`() {
        val data = Actions.startSession(baseData(), null, TODAY, now, titleOverride = "Ad hoc")
        assertEquals("Ad hoc", data.activeSession!!.title)
        assertTrue(data.activeSession!!.exercises.isEmpty())
    }

    // -- logging -------------------------------------------------------------------------------

    @Test
    fun `an added set carries the last set forward and leaves the prescription alone`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val exId = data.activeSession!!.exercises.first().id
        data = Actions.updateSet(
            data, exId, data.activeSession!!.exercises.first().sets.last().id,
            weightKg = 35.0, reps = 6, done = true,
        )
        data = Actions.addSet(data, exId)

        val ex = data.activeSession!!.exercises.first()
        assertEquals(5, ex.sets.size)
        assertEquals(4, ex.plannedSets)          // prescription untouched
        assertEquals(35.0, ex.sets.last().weightKg, 0.001)
        assertFalse(ex.sets.last().done)
    }

    @Test
    fun `ten extra sets are all retained`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val exId = data.activeSession!!.exercises.first().id
        repeat(10) { data = Actions.addSet(data, exId) }
        assertEquals(14, data.activeSession!!.exercises.first().sets.size)
        assertEquals(4, data.activeSession!!.exercises.first().plannedSets)
    }

    @Test
    fun `decimal weights survive unchanged`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val ex = data.activeSession!!.exercises.first { it.exerciseId == "standing-db-curl" }
        data = Actions.updateSet(data, ex.id, ex.sets.first().id, weightKg = 17.5, reps = 10, done = true)
        val stored = data.activeSession!!.exercises.first { it.exerciseId == "standing-db-curl" }
        assertEquals(17.5, stored.sets.first().weightKg, 0.0)
    }

    @Test
    fun `weights and reps can never be driven negative`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.updateSet(data, ex.id, ex.sets.first().id, weightKg = -50.0, reps = -3)
        val stored = data.activeSession!!.exercises.first().sets.first()
        assertEquals(0.0, stored.weightKg, 0.001)
        assertEquals(0, stored.reps)
    }

    @Test
    fun `a set can be removed`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.removeSet(data, ex.id, ex.sets.first().id)
        assertEquals(3, data.activeSession!!.exercises.first().sets.size)
    }

    @Test
    fun `warm-up sets are logged but excluded from volume`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.updateSet(data, ex.id, ex.sets[0].id, weightKg = 20.0, reps = 10, done = true, warmup = true)
        data = Actions.updateSet(data, ex.id, ex.sets[1].id, weightKg = 40.0, reps = 6, done = true)

        val stored = data.activeSession!!.exercises.first()
        assertEquals(240.0, stored.volumeKg, 0.001)
        assertEquals(2, stored.completedCount)
        assertEquals(1, stored.workingSets.size)
    }

    // -- changing the plan mid-session ----------------------------------------------------------

    @Test
    fun `an exercise can be added to a running session`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        data = Actions.addExerciseToSession(data, "hammer-curl", sets = 3)
        val added = data.activeSession!!.exercises.last()
        assertEquals("hammer-curl", added.exerciseId)
        assertEquals(3, added.sets.size)
    }

    @Test
    fun `replacing a movement keeps its prescription and position`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val target = data.activeSession!!.exercises.first()
        data = Actions.replaceExercise(data, target.id, "skull-crusher")

        val replaced = data.activeSession!!.exercises.first()
        assertEquals("skull-crusher", replaced.exerciseId)
        assertEquals(target.plannedSets, replaced.plannedSets)
        assertEquals(target.repLow, replaced.repLow)
        assertEquals(0, data.activeSession!!.exercises.indexOfFirst { it.id == target.id })
    }

    @Test
    fun `replacing a movement keeps sets already completed`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val target = data.activeSession!!.exercises.first()
        data = Actions.updateSet(data, target.id, target.sets.first().id, weightKg = 40.0, reps = 8, done = true)
        data = Actions.replaceExercise(data, target.id, "skull-crusher")

        val replaced = data.activeSession!!.exercises.first()
        assertEquals(40.0, replaced.sets.first().weightKg, 0.001)
        assertTrue(replaced.sets.first().done)
    }

    @Test
    fun `exercises reorder like a drag and drop`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val originalSecond = data.activeSession!!.exercises[1].exerciseId
        data = Actions.moveExercise(data, 1, 0)
        assertEquals(originalSecond, data.activeSession!!.exercises[0].exerciseId)
    }

    @Test
    fun `an out of bounds reorder is ignored rather than crashing`() {
        val data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val moved = Actions.moveExercise(data, 0, 99)
        assertEquals(
            data.activeSession!!.exercises.map { it.exerciseId },
            moved.activeSession!!.exercises.map { it.exerciseId },
        )
    }

    @Test
    fun `a skipped movement keeps its prescription but records nothing`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.setSkipped(data, ex.id, true)
        data = Actions.finishSession(data, now + 3_600_000)

        val stored = data.sessions.single().exercises.first { it.exerciseId == ex.exerciseId }
        assertTrue(stored.skipped)
        assertEquals(0.0, stored.volumeKg, 0.001)
        assertEquals(4, stored.plannedSets)
    }

    @Test
    fun `a target can be adjusted for one session without touching the programme`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.updateTarget(data, ex.id, plannedSets = 6, repLow = 4, repHigh = 6)

        assertEquals(6, data.activeSession!!.exercises.first().plannedSets)
        assertEquals(4, data.routine("routine-arms")!!.items.first().sets)   // programme unchanged
    }

    // -- finishing -----------------------------------------------------------------------------

    @Test
    fun `finishing keeps only the sets actually performed`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.updateSet(data, ex.id, ex.sets[0].id, weightKg = 30.0, reps = 8, done = true)
        data = Actions.updateSet(data, ex.id, ex.sets[1].id, weightKg = 30.0, reps = 7, done = true)
        data = Actions.finishSession(data, now + 3_600_000)

        assertNull(data.activeSession)
        val stored = data.sessions.single()
        val storedEx = stored.exercises.first { it.exerciseId == "close-grip-bench" }
        assertEquals(2, storedEx.sets.size)          // the two untouched rows are dropped
        assertEquals(4, storedEx.plannedSets)        // but the prescription is preserved
        assertEquals(60, stored.durationMinutes)
        assertTrue(stored.finished)
    }

    @Test
    fun `a movement never started is not stored as a row of zeroes`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.updateSet(data, ex.id, ex.sets[0].id, weightKg = 30.0, reps = 8, done = true)
        data = Actions.finishSession(data, now + 60_000)

        assertEquals(1, data.sessions.single().exercises.size)
    }

    @Test
    fun `discarding leaves history untouched`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, now)
        data = Actions.discardSession(data)
        assertNull(data.activeSession)
        assertTrue(data.sessions.isEmpty())
    }

    // -- catalogue and programme -----------------------------------------------------------------

    @Test
    fun `a user added exercise is marked custom and is immediately usable`() {
        var data = baseData()
        val custom = Exercise(id = newId(), name = "Zercher Squat", muscle = Muscle.QUADS)
        data = Actions.addExercise(data, custom)

        assertTrue(data.exercise(custom.id)!!.custom)
        data = Actions.startSession(data, null, TODAY, now)
        data = Actions.addExerciseToSession(data, custom.id)
        assertEquals(custom.id, data.activeSession!!.exercises.single().exerciseId)
    }

    @Test
    fun `archiving an exercise preserves the history that references it`() {
        val history = finishedSession(TODAY, listOf(sessionExercise("bench-press", listOf(set(100.0, 5)))))
        var data = baseData(listOf(history))
        data = Actions.archiveExercise(data, "bench-press")

        assertTrue(data.exercise("bench-press")!!.archived)
        assertNotNull(data.sessions.single().exercises.single().exerciseId)
        assertEquals("Barbell Bench Press", data.exerciseName("bench-press"))
    }

    @Test
    fun `a whole new routine can be created and scheduled`() {
        var data = baseData()
        val routine = Routine(
            id = "custom-routine",
            name = "Sunday Conditioning",
            days = listOf(java.time.DayOfWeek.SUNDAY),
        )
        data = Actions.addRoutine(data, "programme-winter-arc", routine)
        data = Actions.addPlanItem(
            data, "programme-winter-arc", "custom-routine",
            PlanItem(exerciseId = "run", group = "", sets = 1, repLow = 1, repHigh = 1),
        )

        val stored = data.routineFor(java.time.DayOfWeek.SUNDAY)!!
        assertEquals("Sunday Conditioning", stored.name)
        assertEquals("A1", stored.items.single().group)
    }

    @Test
    fun `activating a programme deactivates the others`() {
        var data = baseData()
        val second = Programme(
            id = "programme-2", name = "Off-season",
            startDate = TODAY, endDate = TODAY.plusMonths(3),
        )
        data = Actions.addProgramme(data, second)
        data = Actions.activateProgramme(data, "programme-2")

        assertEquals("programme-2", data.activeProgramme!!.id)
        assertEquals(1, data.programmes.count { it.active })
    }

    // -- body ------------------------------------------------------------------------------------

    @Test
    fun `a second check-in on the same day corrects the first`() {
        var data = baseData()
        data = Actions.saveBodyEntry(data, BodyEntry(date = TODAY, weightKg = 79.0, bodyFatPct = 23.0))
        assertEquals(1, data.body.count { it.date == TODAY })
        assertEquals(79.0, data.latestWeightKg!!, 0.001)
    }

    @Test
    fun `body entries on different days accumulate`() {
        var data = baseData()
        data = Actions.saveBodyEntry(data, BodyEntry(date = TODAY.plusDays(7), weightKg = 79.0))
        assertEquals(2, data.body.size)
        assertEquals(79.0, data.latestWeightKg!!, 0.001)
    }
}
