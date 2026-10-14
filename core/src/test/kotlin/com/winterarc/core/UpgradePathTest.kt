package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The upgrade exactly as a phone performs it: an existing save file on disk, written by the
 * previous build, opened by the new one.
 *
 * The in-memory migration tests prove the transformation. This proves the journey -- parse the
 * old document, migrate, write it back, and read it again on the next launch -- because every
 * step in between is a place the new programme could silently fail to arrive.
 */
class UpgradePathTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** What the previous build left on disk: the old programme, real training, no migrations. */
    private fun fileFromPreviousBuild(): File {
        val previous = File("../app/src/main/assets/seed.json").readText()
        val f = temp.newFile("winter-arc.json")
        f.writeText(previous)
        return f
    }

    /** One app start: read, migrate, persist if anything changed. */
    private fun launch(file: File): AppData {
        val store = FileStore(file)
        val loaded = store.load() ?: error("nothing on disk")
        val migrated = Migrations.apply(loaded)
        if (migrated != loaded) assertTrue("the migrated state was refused", store.save(migrated))
        return migrated
    }

    @Test
    fun `the first launch after the update installs the new programme`() {
        val file = fileFromPreviousBuild()
        val before = FileStore(file).load()!!
        assertEquals("programme-winter-arc", before.activeProgramme?.id)

        val after = launch(file)
        assertEquals(Programme41.ID, after.activeProgramme?.id)
        assertEquals("Winter Arc v4.1", after.activeProgramme?.name)
    }

    @Test
    fun `the new programme is still there on the launch after that`() {
        val file = fileFromPreviousBuild()
        launch(file)

        // A second cold start reads only what the first one wrote.
        val second = launch(file)
        assertEquals(Programme41.ID, second.activeProgramme?.id)
        assertEquals(84, second.activeProgramme!!.routines.sumOf { it.totalSets })

        val third = launch(file)
        assertEquals(Programme41.ID, third.activeProgramme?.id)
    }

    @Test
    fun `what the plan screen would draw is the new week, every day of it`() {
        val file = fileFromPreviousBuild()
        val data = launch(file)
        val programme = data.activeProgramme!!

        WeekDays.forEach { day ->
            val routine = programme.routines.firstOrNull { !it.archived && day in it.days }
            if (day == java.time.DayOfWeek.SUNDAY) {
                assertEquals("Sunday should be rest", null, routine)
            } else {
                assertTrue("No routine drawn for $day", routine != null)
                assertTrue("$day is not a v4.1 routine", routine!!.id.startsWith("v41-"))
            }
        }
    }

    @Test
    fun `the training survives the whole journey`() {
        val file = fileFromPreviousBuild()
        val before = FileStore(file).load()!!
        val after = launch(file)
        val reread = FileStore(file).load()!!

        listOf(after, reread).forEach { state ->
            assertEquals(before.sessions.size, state.sessions.size)
            assertEquals(
                before.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } },
                state.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } },
            )
            assertEquals(before.sessions, state.sessions)
        }
    }

    @Test
    fun `a fresh install lands on the new programme too`() {
        // No file at all: the shipped document is read, then migrated, exactly as on a new phone.
        val shipped = DataCodec.decode(File("../app/src/main/assets/seed.json").readText())
        val file = temp.newFile("fresh.json")
        val store = FileStore(file)
        store.save(shipped)

        val data = launch(file)
        assertEquals(Programme41.ID, data.activeProgramme?.id)
    }
}
