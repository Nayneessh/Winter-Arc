package com.winterarc.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The entire application state, in one serialisable tree.
 *
 * Everything the app knows lives here. A personal training log is small -- roughly a megabyte
 * per training year -- so it is held in memory and written whole. That buys atomic saves, a
 * trivially correct export, and a data layer with no annotation processor, no schema migration
 * and no query language to get wrong. If the volume ever outgrew that, this is the one type a
 * database would have to replace, and nothing above it would change.
 */
@Serializable
data class AppData(
    val version: Int = CURRENT_VERSION,
    val exercises: List<Exercise> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val body: List<BodyEntry> = emptyList(),
    val goals: Goals = Goals(),
    val prefs: Prefs = Prefs(),
    /** The workout currently being performed, if any. Kept apart from finished history. */
    val activeSession: Session? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }

    /**
     * True for a state that cannot have come from the user.
     *
     * Every legitimate state has a catalogue: a fresh install seeds one, and erasing everything
     * restores one. A tree with no exercises, no programme, no sessions and no check-ins is
     * therefore always a bug -- an uninitialised value that escaped before loading finished --
     * and must never be allowed to reach the disk on top of real training.
     */
    val isBlank: Boolean
        get() = exercises.isEmpty() && programmes.isEmpty() && sessions.isEmpty() && body.isEmpty()

    val exerciseById: Map<String, Exercise> get() = exercises.associateBy { it.id }

    fun exercise(id: String): Exercise? = exercises.firstOrNull { it.id == id }

    fun exerciseName(id: String): String = exercise(id)?.name ?: "Unknown exercise"

    val activeProgramme: Programme? get() = programmes.firstOrNull { it.active }

    fun routine(id: String?): Routine? =
        id?.let { rid -> programmes.firstNotNullOfOrNull { p -> p.routines.firstOrNull { it.id == rid } } }

    fun routineFor(day: DayOfWeek): Routine? = activeProgramme?.routineFor(day)

    /** Finished sessions, newest first. The only list history and analytics should ever read. */
    val history: List<Session>
        get() = sessions.filter { it.finished }.sortedWith(
            compareByDescending<Session> { it.date }.thenByDescending { it.startedAtMillis },
        )

    val bodyByDate: List<BodyEntry> get() = body.sortedBy { it.date }

    val latestBody: BodyEntry? get() = bodyByDate.lastOrNull()

    /** Most recent recorded scale weight, from a body entry that actually carried one. */
    val latestWeightKg: Double? get() = bodyByDate.lastOrNull { it.weightKg != null }?.weightKg

    val latestBodyFatPct: Double? get() = bodyByDate.lastOrNull { it.bodyFatPct != null }?.bodyFatPct
}

/**
 * JSON codec.
 *
 * [Json.ignoreUnknownKeys] is on so that a file written by a newer build -- one that has since
 * added a field -- still loads on an older one instead of throwing away the user's history.
 */
object DataCodec {
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(data: AppData): String = json.encodeToString(data)

    fun decode(text: String): AppData = json.decodeFromString(AppData.serializer(), text)
}

/**
 * File-backed persistence with an atomic write and one generation of backup.
 *
 * The failure this guards against is a crash or a kill mid-save leaving a truncated file, which
 * on a whole-file store means losing everything rather than one record. Saves therefore go to a
 * temporary file first and are renamed into place only once fully written; the previous good
 * file is kept as `.bak` and is read if the main file will not parse.
 */
class FileStore(private val file: File) {

    private val tempFile = File(file.parentFile, file.name + ".tmp")
    private val backupFile = File(file.parentFile, file.name + ".bak")

    /**
     * Returns the best readable state, or null when there is genuinely nothing yet.
     *
     * The backup used to be consulted only when the main file failed to PARSE -- which missed the
     * case that actually cost a user their training. A file wiped by a bug is still valid JSON, so
     * it parsed, was returned, and the real history sitting in the backup was never looked at.
     *
     * A blank main file is therefore treated as a reason to look further, not as an answer.
     */
    fun load(): AppData? {
        val primary = readParse(file)
        if (primary != null && !primary.isBlank) return primary

        val backup = readParse(backupFile)
        if (backup != null && !backup.isBlank) return backup

        return primary ?: backup
    }

    /** True when the backup holds real training the main file does not. Recovery is possible. */
    fun hasRecoverableBackup(): Boolean {
        val primary = readParse(file)
        if (primary != null && !primary.isBlank) return false
        val backup = readParse(backupFile)
        return backup != null && !backup.isBlank
    }

    private fun readParse(f: File): AppData? = try {
        if (f.exists() && f.length() > 0) DataCodec.decode(f.readText()) else null
    } catch (_: Exception) {
        null
    }

    /**
     * Returns false when the write was refused.
     *
     * A blank tree is never written over a file that holds real data. This is the last line of
     * defence rather than the first: the caller should never ask, but a save is the one operation
     * that destroys history irreversibly, so it checks for itself instead of trusting that it was
     * only ever called correctly.
     */
    fun save(data: AppData): Boolean {
        if (data.isBlank) {
            val existing = load()
            if (existing != null && !existing.isBlank) return false
        }
        file.parentFile?.mkdirs()
        val text = DataCodec.encode(data)
        tempFile.writeText(text)
        // The backup is only ever replaced by something worth keeping. Promoting a blank file to
        // backup is how the last copy of a training log gets destroyed: the main file is already
        // ruined, and copying it over the backup spends the one remaining chance to recover.
        if (file.exists() && readParse(file)?.isBlank == false) {
            backupFile.delete()
            file.copyTo(backupFile, overwrite = true)
        }
        if (!tempFile.renameTo(file)) {
            // renameTo can fail across some filesystems; fall back to a direct write, which is
            // still safe because the backup written above is known-good.
            file.writeText(text)
            tempFile.delete()
        }
        return true
    }

    fun exportJson(data: AppData): String = DataCodec.encode(data)
}

/**
 * CSV export of every set ever performed -- one row per set, which is the grain any spreadsheet
 * or analysis tool will want. Values are emitted in kilograms regardless of display preference,
 * so an export is unambiguous.
 */
object CsvExport {
    fun sets(data: AppData): String {
        val sb = StringBuilder()
        sb.append("date,session,exercise,muscle,group,set_index,weight_kg,reps,volume_kg,warmup,completed,rpe\n")
        for (session in data.sessions.filter { it.finished }.sortedBy { it.date }) {
            for (ex in session.exercises) {
                val exercise = data.exercise(ex.exerciseId)
                ex.sets.forEachIndexed { index, set ->
                    sb.append(session.date).append(',')
                        .append(escape(session.title)).append(',')
                        .append(escape(exercise?.name ?: "Unknown")).append(',')
                        .append(exercise?.muscle?.display ?: "").append(',')
                        .append(escape(ex.group)).append(',')
                        .append(index + 1).append(',')
                        .append(trim(set.weightKg)).append(',')
                        .append(set.reps).append(',')
                        .append(trim(set.volumeKg)).append(',')
                        .append(set.warmup).append(',')
                        .append(set.done).append(',')
                        .append(set.rpe?.let { trim(it) } ?: "")
                        .append('\n')
                }
            }
        }
        return sb.toString()
    }

    fun body(data: AppData): String {
        val sites = MeasurementSites.ordered.map { it.first }
        val sb = StringBuilder()
        sb.append("date,weight_kg,body_fat_pct,lean_mass_kg,fat_mass_kg")
        sites.forEach { sb.append(',').append(it).append("_cm") }
        sb.append(",note\n")
        for (entry in data.bodyByDate) {
            sb.append(entry.date).append(',')
                .append(entry.weightKg?.let { trim(it) } ?: "").append(',')
                .append(entry.bodyFatPct?.let { trim(it) } ?: "").append(',')
                .append(entry.leanMassKg?.let { trim(it) } ?: "").append(',')
                .append(entry.fatMassKg?.let { trim(it) } ?: "")
            sites.forEach { site -> sb.append(',').append(entry.measurementsCm[site]?.let { trim(it) } ?: "") }
            sb.append(',').append(escape(entry.note)).append('\n')
        }
        return sb.toString()
    }

    private fun trim(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else {
            value
        }
}

/** A stable "today" seam so date-dependent logic can be tested without waiting for midnight. */
fun interface Clock {
    fun today(): LocalDate

    companion object {
        val System = Clock { LocalDate.now() }
        fun fixed(date: LocalDate) = Clock { date }
    }
}
