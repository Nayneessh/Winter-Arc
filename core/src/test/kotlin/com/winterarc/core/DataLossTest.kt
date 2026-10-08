package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/**
 * Regression cover for the one failure that cannot be apologised away: losing a training log.
 *
 * The app read its save file on a background thread while the rest of it ran against an empty
 * placeholder. Stopping the activity before that read finished flushed the placeholder over the
 * real file, and the next launch read back a valid, empty history. Four sessions went that way.
 *
 * The race itself is fixed by loading synchronously, but a race is a timing argument and timing
 * arguments rot. These tests pin the invariant underneath it instead: a blank tree never lands on
 * top of real training, no matter who asks or why.
 */
class DataLossTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val real: AppData get() = Seed.initial(LocalDate.of(2026, 9, 5))

    private fun store(): Pair<FileStore, File> {
        val file = File(folder.root, "winter-arc.json")
        return FileStore(file) to file
    }

    @Test
    fun `an empty tree is recognised as one that cannot have come from the user`() {
        assertTrue(AppData().isBlank)
        assertFalse(real.isBlank)
    }

    @Test
    fun `erasing everything still leaves a catalogue, so it is never mistaken for a blank tree`() {
        // "Erase everything" restores the seed. If that were blank, the guard would be unable to
        // tell a real reset from the bug, and would have to let the bug through.
        assertFalse(Seed.initial(LocalDate.now()).isBlank)
    }

    @Test
    fun `the exact bug - flushing a placeholder over real training is refused`() {
        val (store, _) = store()
        store.save(real)

        // What MainActivity.onStop did while the load was still in flight.
        val wrote = store.save(AppData())

        assertFalse("The write must be refused", wrote)
        val reloaded = store.load()!!
        assertFalse(reloaded.isBlank)
        assertEquals(real.exercises.size, reloaded.exercises.size)
        assertEquals(real.sessions.size, reloaded.sessions.size)
    }

    @Test
    fun `a refused write leaves the backup intact too`() {
        val (store, file) = store()
        store.save(real)
        store.save(real.copy(prefs = Prefs(unit = WeightUnit.LB)))   // creates the .bak

        store.save(AppData())

        assertTrue(file.exists())
        assertFalse(store.load()!!.isBlank)
        val backup = File(folder.root, "winter-arc.json.bak")
        assertTrue("The previous good file must survive", backup.exists())
        assertFalse(DataCodec.decode(backup.readText()).isBlank)
    }

    @Test
    fun `repeated placeholder flushes cannot grind the file down`() {
        val (store, _) = store()
        store.save(real)
        repeat(10) { store.save(AppData()) }
        assertEquals(real.sessions.size, store.load()!!.sessions.size)
    }

    @Test
    fun `a blank tree is still writable when there is nothing to lose`() {
        // Refusing unconditionally would break a genuinely empty first run.
        val (store, _) = store()
        assertTrue(store.save(AppData()))
        assertNotNull(store.load())
    }

    @Test
    fun `real training still saves normally`() {
        val (store, _) = store()
        assertTrue(store.save(real))
        var data = Actions.startSession(store.load()!!, "routine-arms", LocalDate.of(2026, 9, 12), 1L)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.updateSet(data, ex.id, ex.sets.first().id, weightKg = 60.0, reps = 6, done = true)
        data = Actions.finishSession(data, 2L)

        assertTrue(store.save(data))
        assertEquals(real.sessions.size + 1, store.load()!!.sessions.size)
    }

    /**
     * The recovery path for a device already wiped by the bug: the file parses, so the old code
     * loaded it and showed an empty app. Treating blank as "no data" sends it back through seeding.
     */
    @Test
    fun `a file already wiped by the bug is detected so it can be reseeded`() {
        val file = File(folder.root, "winter-arc.json")
        file.writeText(DataCodec.encode(AppData()))

        val loaded = FileStore(file).load()
        assertNotNull("It parses, which is exactly why it was shown as an empty app", loaded)
        assertTrue("and it must be recognised as needing recovery", loaded!!.isBlank)
    }
}
