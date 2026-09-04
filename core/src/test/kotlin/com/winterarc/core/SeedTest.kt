package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class SeedTest {

    @Test
    fun `exercise ids are unique`() {
        val ids = Seed.catalogue.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `every prescribed movement exists in the catalogue`() {
        val ids = Seed.catalogue.map { it.id }.toSet()
        val missing = Seed.programme.routines
            .flatMap { it.items }
            .map { it.exerciseId }
            .filterNot { it in ids }
        assertTrue("Plan references unknown exercises: $missing", missing.isEmpty())
    }

    @Test
    fun `every lift goal points at a real exercise`() {
        val ids = Seed.catalogue.map { it.id }.toSet()
        val data = Seed.initial(TODAY)
        data.goals.liftGoals.forEach { assertTrue(it.exerciseId in ids) }
    }

    @Test
    fun `six training days are scheduled and Sunday is rest`() {
        val programme = Seed.programme
        listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        ).forEach { day ->
            assertNotNull("No routine for $day", programme.routineFor(day))
        }
        assertNull(programme.routineFor(DayOfWeek.SUNDAY))
    }

    @Test
    fun `no day is claimed by two routines`() {
        val days = Seed.programme.routines.flatMap { it.days }
        assertEquals(days.size, days.distinct().size)
    }

    @Test
    fun `arm day carries the arm priority on every movement`() {
        val armDay = Seed.programme.routines.first { it.id == "routine-arms" }
        assertTrue(armDay.items.all { it.priority == Priority.ARMS })
        assertEquals(Accent.GOLD, armDay.accent)
    }

    @Test
    fun `rep ranges are ordered and sets are positive`() {
        Seed.programme.routines.flatMap { it.items }.forEach { item ->
            assertTrue("${item.exerciseId} has repLow > repHigh", item.repLow <= item.repHigh)
            assertTrue("${item.exerciseId} has no sets", item.sets > 0)
            assertTrue("${item.exerciseId} has no cue", item.cue.isNotBlank())
        }
    }

    @Test
    fun `the seeded goals match the plan`() {
        val data = Seed.initial(TODAY)
        assertEquals(80.0, data.goals.startWeightKg!!, 0.001)
        assertEquals(73.0, data.goals.targetWeightKg!!, 0.001)
        assertEquals(24.0, data.goals.startBodyFatPct!!, 0.001)
        assertEquals(15.0, data.goals.targetBodyFatPct!!, 0.001)

        val curl = data.goals.liftGoals.single()
        assertEquals(17.5, curl.startKg, 0.001)
        assertEquals(21.0, curl.milestoneKg, 0.001)
        assertEquals(25.0, curl.targetKg, 0.001)
    }

    @Test
    fun `supersets are declared with shared group letters`() {
        val pull = Seed.programme.routines.first { it.id == "routine-pull" }
        val b = pull.items.filter { it.groupLetter == "B" }
        assertEquals(2, b.size)
        assertEquals(listOf("B1", "B2"), b.map { it.group })
    }
}
