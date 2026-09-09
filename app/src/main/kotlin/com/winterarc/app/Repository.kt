package com.winterarc.app

import com.winterarc.core.AppData
import com.winterarc.core.CsvExport
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

    fun load() {
        scope.launch {
            val existing = store.load()
            if (existing != null) {
                _data.value = existing
            } else {
                // First run. The seed is written immediately so that the programme the user sees
                // is already their own editable data, not a template consulted again later.
                val seeded = loadSeed()
                _data.value = seeded
                store.save(seeded)
            }
            _ready.value = true
        }
    }

    fun update(block: (AppData) -> AppData) {
        _data.value = block(_data.value)
        scheduleSave()
    }

    private fun scheduleSave() {
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
     * not be lost to the process being killed while the user is out of the app.
     */
    fun flush() {
        saveJob?.cancel()
        runBlocking(Dispatchers.IO) { store.save(_data.value) }
    }

    fun exportJson(): String = store.exportJson(_data.value)

    fun exportSetsCsv(): String = CsvExport.sets(_data.value)

    fun exportBodyCsv(): String = CsvExport.body(_data.value)

    /** Replaces everything from a previously exported file. Used by Restore in settings. */
    fun importJson(text: String): Boolean = try {
        val parsed = com.winterarc.core.DataCodec.decode(text)
        _data.value = parsed
        store.save(parsed)
        true
    } catch (_: Exception) {
        false
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
