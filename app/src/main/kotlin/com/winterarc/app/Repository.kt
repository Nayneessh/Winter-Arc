package com.winterarc.app

import com.winterarc.core.AppData
import com.winterarc.core.CsvExport
import com.winterarc.core.DataCodec
import com.winterarc.core.FileStore
import com.winterarc.core.Seed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate

/**
 * Holds the whole application state and keeps it on disk.
 *
 * State lives in memory as a single immutable [AppData]; every mutation replaces it wholesale
 * and schedules a save. Writes are debounced because logging a set is a rapid burst of edits --
 * weight, reps, tick -- and rewriting the file on each keystroke would put IO in the way of the
 * one interaction that has to feel instant.
 */
class Repository(
    dataFile: File,
    /**
     * The seed document shipped inside the APK, read on first run only.
     *
     * It is a real export in the app's own save format, so the training already recorded arrives
     * intact rather than being transcribed into code and drifting from what actually happened.
     * Returns null when there is none, in which case the code-built seed is used.
     */
    private val shippedSeed: () -> String? = { null },
) {

    private val store = FileStore(dataFile)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var saveJob: Job? = null

    /**
     * Called with the encoded state whenever it is committed to disk.
     *
     * The off-device backup hangs off this rather than off every edit: it fires when the app goes
     * to the background, which is often enough to be safe and rare enough not to thrash storage.
     */
    var onPersisted: ((AppData, String) -> Unit)? = null

    /**
     * Reads the save file. Synchronous, and that is the point.
     *
     * This used to run on a background dispatcher while the rest of the app carried on against an
     * empty placeholder state. If the activity was stopped before that read finished -- backgrounded
     * straight after launch, or simply recreated -- onStop flushed the placeholder over the real
     * file, and the next launch read back a perfectly valid, perfectly empty training history. It
     * destroyed data.
     *
     * Reading here instead removes the window entirely: nothing can observe or persist state before
     * it exists. It costs a few milliseconds of a cold start, before any frame is drawn, which is a
     * trade worth making many times over to never lose a training log.
     */
    fun load() {
        val existing = store.load()
        _data.value = when {
            existing == null -> seedAndPersist()
            // A stored file with nothing in it is not a user who deleted everything -- erasing
            // restores a catalogue. It is the wiped file the race above produced, so it is treated
            // as a first run and the shipped training is restored rather than shown as an empty app.
            existing.isBlank -> seedAndPersist()
            else -> existing
        }
        _ready.value = true
    }

    private fun seedAndPersist(): AppData {
        val seeded = loadSeed()
        store.save(seeded)
        return seeded
    }

    fun update(block: (AppData) -> AppData) {
        // Nothing may mutate or persist before the load has happened: that is how a placeholder
        // reached the disk in the first place.
        if (!_ready.value) return
        _data.value = block(_data.value)
        scheduleSave()
    }

    private fun scheduleSave() {
        if (!_ready.value) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            store.save(_data.value)
        }
    }

    /**
     * Writes immediately, blocking until done.
     *
     * Called when the app goes to the background: a debounced save that has not fired yet must
     * not be lost to the process being killed while the user is out of the app. It does nothing
     * before the load has completed, because at that point there is nothing worth writing and
     * everything to lose.
     */
    fun flush() {
        if (!_ready.value) return
        saveJob?.cancel()
        runBlocking(Dispatchers.IO) {
            val snapshot = _data.value
            if (store.save(snapshot)) {
                runCatching { onPersisted?.invoke(snapshot, DataCodec.encode(snapshot)) }
            }
        }
    }

    /** Dated copies held on the device, newest first. */
    fun snapshots(): List<com.winterarc.core.Snapshot> = store.snapshots()

    /** Puts a dated copy back. Returns false if it could not be read. */
    fun restoreSnapshot(name: String): Boolean {
        val restored = store.readSnapshot(name) ?: return false
        _data.value = restored
        store.save(restored)
        return true
    }

    fun exportJson(): String = store.exportJson(_data.value)

    fun exportSetsCsv(): String = CsvExport.sets(_data.value)

    fun exportBodyCsv(): String = CsvExport.body(_data.value)

    /**
     * Decodes a backup without applying it.
     *
     * Restoring is the one routine action that can silently undo months of training -- an older
     * export replaces a newer one and nothing looks wrong. So the file is read first, shown, and
     * only applied once the difference has been seen.
     */
    fun previewImport(text: String): AppData? = try {
        com.winterarc.core.DataCodec.decode(text).takeIf { !it.isBlank }
    } catch (_: Exception) {
        null
    }

    fun applyImport(parsed: AppData) {
        if (!_ready.value) return
        _data.value = parsed
        store.save(parsed)   // takes a safety copy first if this loses sessions
    }

    fun resetToSeed() {
        val seeded = loadSeed()
        _data.value = seeded
        store.save(seeded)
    }

    /**
     * The state a fresh install starts from: the shipped document if it is present and readable,
     * otherwise the programme built in code. A corrupt asset must never stop the app opening, so
     * a failure to parse falls through rather than propagating.
     */
    private fun loadSeed(): AppData {
        val text = try {
            shippedSeed()
        } catch (_: Exception) {
            null
        }
        if (text != null) {
            try {
                return com.winterarc.core.DataCodec.decode(text)
            } catch (_: Exception) {
                // fall through to the code-built seed
            }
        }
        return Seed.initial(LocalDate.now())
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 350L
    }
}
