package com.winterarc.app.data.repo

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.winterarc.domain.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data export.
 *
 * The user owns their training history and must be able to take it elsewhere. Two formats
 * are produced: JSON preserving the full nested structure, and a flat CSV with one row per
 * performed set, which is what a spreadsheet actually wants.
 */
class DataExporter(private val context: Context, private val repo: TrainingRepository) {

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    suspend fun exportJson(): File = withContext(Dispatchers.IO) {
        val sessions = repo.completedHistory().sortedBy { it.date }
        val body = repo.allBodyMetrics().sortedBy { it.date }
        val names = repo.allExercises().associate { it.id to it.name }

        val sb = StringBuilder()
        sb.append("{\n  \"exportedAt\": \"${LocalDateTime.now()}\",\n")
        sb.append("  \"app\": \"Winter Arc\",\n")
        sb.append("  \"volumeDefinition\": \"weight_kg * reps, working sets only\",\n")
        sb.append("  \"oneRepMaxFormula\": \"Epley: weight * (1 + reps / 30)\",\n")
        sb.append("  \"sessions\": [\n")
        sessions.forEachIndexed { i, s ->
            sb.append("    {\n")
            sb.append("      \"id\": ${s.id.json()},\n")
            sb.append("      \"date\": \"${s.date}\",\n")
            sb.append("      \"name\": ${s.name.json()},\n")
            sb.append("      \"durationSeconds\": ${s.durationSeconds ?: 0},\n")
            sb.append("      \"plannedSets\": ${s.plannedSetTotal},\n")
            sb.append("      \"actualSets\": ${s.actualSetTotal},\n")
            sb.append("      \"extraSets\": ${s.extraSetTotal},\n")
            sb.append("      \"totalReps\": ${s.totalReps},\n")
            sb.append("      \"totalVolumeKg\": ${s.totalVolumeKg},\n")
            sb.append("      \"notes\": ${s.notes.orEmpty().json()},\n")
            sb.append("      \"exercises\": [\n")
            s.exercises.sortedBy { it.position }.forEachIndexed { j, pe ->
                sb.append("        {\n")
                sb.append("          \"exercise\": ${(names[pe.exerciseId] ?: pe.exerciseId).json()},\n")
                sb.append("          \"plannedSets\": ${pe.plannedSets},\n")
                sb.append("          \"plannedReps\": \"${pe.plannedRepLow}-${pe.plannedRepHigh}\",\n")
                sb.append("          \"plannedWeightKg\": ${pe.plannedWeightKg ?: "null"},\n")
                sb.append("          \"extraSets\": ${pe.extraSetCount},\n")
                sb.append("          \"replacedExercise\": ${pe.replacedExerciseId?.let { (names[it] ?: it).json() } ?: "null"},\n")
                sb.append("          \"skipped\": ${pe.isSkipped},\n")
                sb.append("          \"sets\": [")
                sb.append(
                    pe.sets.sortedBy { it.setNumber }.joinToString(", ") { set ->
                        "{\"set\": ${set.setNumber}, \"weightKg\": ${set.weightKg}, " +
                            "\"reps\": ${set.reps}, \"warmup\": ${set.isWarmup}}"
                    },
                )
                sb.append("]\n        }${if (j < s.exercises.lastIndex) "," else ""}\n")
            }
            sb.append("      ]\n    }${if (i < sessions.lastIndex) "," else ""}\n")
        }
        sb.append("  ],\n  \"bodyMetrics\": [\n")
        body.forEachIndexed { i, m ->
            sb.append("    {\"date\": \"${m.date}\", \"weightKg\": ${m.weightKg ?: "null"}, ")
            sb.append("\"chestCm\": ${m.chestCm ?: "null"}, \"waistCm\": ${m.waistCm ?: "null"}, ")
            sb.append("\"bicepsLeftCm\": ${m.bicepsLeftCm ?: "null"}, \"bicepsRightCm\": ${m.bicepsRightCm ?: "null"}, ")
            sb.append("\"shouldersCm\": ${m.shouldersCm ?: "null"}, \"thighCm\": ${m.thighCm ?: "null"}, ")
            sb.append("\"calfCm\": ${m.calfCm ?: "null"}, \"neckCm\": ${m.neckCm ?: "null"}, ")
            sb.append("\"forearmCm\": ${m.forearmCm ?: "null"}, \"bodyFatPercent\": ${m.bodyFatPercent ?: "null"}}")
            sb.append(if (i < body.lastIndex) ",\n" else "\n")
        }
        sb.append("  ]\n}\n")

        write("winter-arc-${LocalDateTime.now().format(stamp)}.json", sb.toString())
    }

    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val sessions = repo.completedHistory().sortedBy { it.date }
        val names = repo.allExercises().associate { it.id to it.name }

        val sb = StringBuilder()
        sb.append("date,session,exercise,set_number,is_warmup,is_extra,weight_kg,reps,volume_kg,")
        sb.append("planned_sets,planned_rep_low,planned_rep_high,planned_weight_kg,replaced_exercise\n")
        sessions.forEach { s ->
            s.exercises.sortedBy { it.position }.forEach { pe ->
                pe.sets.sortedBy { it.setNumber }.forEach { set ->
                    val isExtra = set.setNumber > pe.plannedSets && !set.isWarmup
                    sb.append("${s.date},${s.name.csv()},${(names[pe.exerciseId] ?: pe.exerciseId).csv()},")
                    sb.append("${set.setNumber},${set.isWarmup},$isExtra,${set.weightKg},${set.reps},")
                    sb.append("${set.volumeKg},${pe.plannedSets},${pe.plannedRepLow},${pe.plannedRepHigh},")
                    sb.append("${pe.plannedWeightKg ?: ""},${pe.replacedExerciseId?.let { names[it] ?: it }?.csv() ?: ""}\n")
                }
            }
        }
        write("winter-arc-sets-${LocalDateTime.now().format(stamp)}.csv", sb.toString())
    }

    private fun write(name: String, content: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name).apply { writeText(content) }
    }

    /** Wraps the file in a share intent via FileProvider — no external storage permission needed. */
    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "csv") "text/csv" else "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Winter Arc export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun String.json(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun String.csv(): String =
        if (contains(',') || contains('"')) "\"" + replace("\"", "\"\"") + "\"" else this
}
