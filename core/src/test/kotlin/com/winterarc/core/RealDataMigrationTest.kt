package com.winterarc.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The migration run against the user's own training file, not a fixture.
 *
 * Fixtures prove the logic; this proves the case. The document under test is the real backup the
 * app ships and restores from -- the same shape the installed app holds on disk -- so if the
 * programme change would cost a session, a set or a movement on the actual device, it fails here.
 */
class RealDataMigrationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val file = File("../app/src/main/assets/seed.json")

    private fun realData(): AppData =
        json.decodeFromString(AppData.serializer(), file.readText())

    @Test
    fun `the shipped training file is present and readable`() {
        assertTrue("seed.json is missing: ${file.absolutePath}", file.isFile)
        val data = realData()
        assertEquals(4, data.sessions.size)
        assertEquals(67, data.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } })
    }

    @Test
    fun `migrating it loses no session, no set, no rep and no kilogram`() {
        val before = realData()
        val after = Migrations.apply(before)

        assertEquals(before.sessions.size, after.sessions.size)
        assertEquals(before.sessions, after.sessions)
        assertEquals(
            before.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } },
            after.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } },
        )
        assertEquals(before.sessions.sumOf { it.totalReps }, after.sessions.sumOf { it.totalReps })
        assertEquals(before.sessions.sumOf { it.volumeKg }, after.sessions.sumOf { it.volumeKg }, 0.0)
    }

    @Test
    fun `the two movements the user added themselves come through`() {
        val after = Migrations.apply(realData())
        listOf("Weighted Leg Raises", "Basian Cable Curls").forEach { name ->
            assertNotNull(
                "$name was lost",
                after.exercises.firstOrNull { it.name == name },
            )
        }
    }

    @Test
    fun `every movement in the file is still there afterwards`() {
        val before = realData()
        val after = Migrations.apply(before)
        before.exercises.forEach { original ->
            assertEquals(
                "${original.name} changed or vanished",
                original,
                after.exercises.firstOrNull { it.id == original.id },
            )
        }
        assertTrue(after.exercises.size >= before.exercises.size)
    }

    @Test
    fun `body check-ins, goals and preferences come through untouched`() {
        val before = realData()
        val after = Migrations.apply(before)
        assertEquals(before.body, after.body)
        assertEquals(before.goals, after.goals)
        assertEquals(before.prefs, after.prefs)
    }

    @Test
    fun `the new plan is in use and the old one is kept for history`() {
        val after = Migrations.apply(realData())
        assertEquals(Programme41.ID, after.activeProgramme!!.id)
        assertEquals(1, after.programmes.count { it.active })
        assertNotNull(after.programmes.firstOrNull { it.id == "programme-winter-arc" })

        // Every routine a past session was performed under still resolves, so history keeps
        // its titles instead of degrading to "Unknown".
        after.sessions.mapNotNull { it.routineId }.forEach { id ->
            assertNotNull("Session referenced routine $id, which no longer resolves", after.routine(id))
        }
    }

    @Test
    fun `it survives being applied repeatedly, as every app start would`() {
        var data = Migrations.apply(realData())
        val settled = data
        repeat(50) { data = Migrations.apply(data) }
        assertEquals(settled, data)
    }

    @Test
    fun `the migrated document round-trips through storage without loss`() {
        val after = Migrations.apply(realData())
        val text = json.encodeToString(AppData.serializer(), after)
        val reread = json.decodeFromString(AppData.serializer(), text)
        assertEquals(after, reread)
        assertEquals(67, reread.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } })
    }

    @Test
    fun `the migrated document is never mistaken for an empty one`() {
        // The blank-write guard is what stopped the data loss. A migrated file must read as real
        // training to it, or a save could be refused -- or worse, treated as a first run.
        val after = Migrations.apply(realData())
        assertTrue(!after.isBlank)
        assertEquals(4, after.finishedSessionCount)
    }
}
