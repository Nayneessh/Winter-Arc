package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Guards the training document shipped inside the APK.
 *
 * The file is a real export from the running app, so it is data rather than code and nothing else
 * would notice if it went missing, failed to parse, or lost a session. These assertions are the
 * only thing standing between a bad edit and a build that silently ships an empty history.
 */
class ShippedSeedTest {

    private val data: AppData by lazy {
        val candidates = listOf(
            File("../app/src/main/assets/seed.json"),
            File("app/src/main/assets/seed.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("The shipped seed is missing. Looked in: ${candidates.joinToString()}")
        DataCodec.decode(file.readText())
    }

    @Test
    fun `the shipped document parses into the current model`() {
        assertEquals(AppData.CURRENT_VERSION, data.version)
        assertNull("A half-finished session must not ship", data.activeSession)
    }

    @Test
    fun `every session recorded so far is present`() {
        val sessions = data.history.sortedBy { it.date }
        assertEquals(4, sessions.size)
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9),
            ),
            sessions.map { it.date },
        )
        assertEquals(
            listOf("Arm Day", "MMA + Back & Rear Delt", "Heavy Push + Quads", "MMA + Arms"),
            sessions.map { it.title },
        )
    }

    @Test
    fun `the totals match the exported figures exactly`() {
        val sessions = data.history
        assertEquals(67, sessions.sumOf { it.workingSetCount })
        assertEquals(607, sessions.sumOf { it.totalReps })
        assertEquals(20_656.5, sessions.sumOf { it.volumeKg }, 0.001)
    }

    @Test
    fun `each session keeps its own figures`() {
        fun volumeOn(date: LocalDate) =
            data.history.first { it.date == date }.volumeKg

        assertEquals(5627.5, volumeOn(LocalDate.of(2026, 9, 5)), 0.001)
        assertEquals(4260.0, volumeOn(LocalDate.of(2026, 9, 7)), 0.001)
        assertEquals(7939.0, volumeOn(LocalDate.of(2026, 9, 8)), 0.001)
        assertEquals(2830.0, volumeOn(LocalDate.of(2026, 9, 9)), 0.001)
    }

    @Test
    fun `the movements added by hand survive`() {
        val custom = data.exercises.filter { it.custom }.map { it.name }.sorted()
        assertEquals(listOf("Basian Cable Curls", "Weighted Leg Raises"), custom)
    }

    @Test
    fun `a hand-added movement is actually referenced by the training that used it`() {
        val basian = data.exercises.first { it.name == "Basian Cable Curls" }
        val used = data.history.any { session ->
            session.exercises.any { it.exerciseId == basian.id && it.workingSets.isNotEmpty() }
        }
        assertTrue("Basian Cable Curls was logged on 9 Sep and must still resolve", used)
        assertEquals(Muscle.BICEPS, basian.muscle)
    }

    /**
     * The failure this catches is the quiet one: a session pointing at a movement that is not in
     * the catalogue renders as "Unknown movement" and drops out of every per-exercise figure,
     * without anything erroring.
     */
    @Test
    fun `no session references a movement that does not exist`() {
        val known = data.exercises.map { it.id }.toSet()
        val orphans = data.sessions
            .flatMap { s -> s.exercises.map { it.exerciseId } }
            .filterNot { it in known }
            .distinct()
        assertTrue("Sessions reference unknown movements: $orphans", orphans.isEmpty())
    }

    @Test
    fun `no routine references a movement that does not exist`() {
        val known = data.exercises.map { it.id }.toSet()
        val orphans = data.programmes
            .flatMap { p -> p.routines.flatMap { r -> r.items.map { it.exerciseId } } }
            .filterNot { it in known }
            .distinct()
        assertTrue("Routines reference unknown movements: $orphans", orphans.isEmpty())
    }

    @Test
    fun `the programme and targets are intact`() {
        val programme = data.activeProgramme!!
        assertEquals("Winter Arc", programme.name)
        assertEquals(6, programme.routines.size)
        assertEquals(80.0, data.goals.startWeightKg!!, 0.001)
        assertEquals(73.0, data.goals.targetWeightKg!!, 0.001)
        assertEquals(24.0, data.goals.startBodyFatPct!!, 0.001)
        assertEquals(15.0, data.goals.targetBodyFatPct!!, 0.001)
        assertEquals(25.0, data.goals.liftGoals.single().targetKg, 0.001)
    }

    @Test
    fun `the set-by-set breakdown reads the shipped training`() {
        val latest = data.history.first()
        val breakdown = Analytics.sessionBreakdown(latest, data.sessions, data.exerciseById)
        assertTrue(breakdown.exercises.isNotEmpty())
        assertTrue(breakdown.exercises.all { it.name != "Unknown movement" })
        // Pushdown on 9 Sep climbed 60 -> 65 and then held.
        val pushdown = breakdown.exercises.first { it.exerciseId == "pushdown" }
        assertEquals(
            listOf(SetDirection.FIRST, SetDirection.UP, SetDirection.HELD),
            pushdown.sets.map { it.direction },
        )
        assertEquals(65.0, pushdown.topWeightKg, 0.001)
    }

    @Test
    fun `personal records are detected across the shipped sessions`() {
        val records = Analytics.allRecords(data.history, data.exerciseById)
        // Pushdown went 55 kg on 5 Sep to 65 kg on 9 Sep, which is a weight record.
        assertTrue(
            "Expected a pushdown weight record",
            records.any { it.exerciseId == "pushdown" && it.type == PrType.WEIGHT },
        )
    }
}
