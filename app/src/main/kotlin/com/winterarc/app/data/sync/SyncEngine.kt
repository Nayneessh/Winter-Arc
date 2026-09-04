package com.winterarc.app.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.winterarc.app.data.db.WinterArcDatabase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

private val Context.syncStore by preferencesDataStore(name = "winter_arc_sync")

/**
 * Push-based synchronisation to Supabase.
 *
 * DESIGN POSITION: the local Room database is the source of truth and the app is fully
 * usable with sync switched off, signed out, or offline indefinitely. Sync is a backup and
 * a second-device convenience, never a dependency of logging a set.
 *
 * CONFLICT RESOLUTION
 *  - Rows are keyed by client-generated UUIDs, so two devices creating data offline can
 *    never collide on identity.
 *  - Pushes are upserts with merge-duplicates, making a retry after a lost response
 *    idempotent rather than a duplicate-key failure.
 *  - Where the same row is edited on two devices, the later updated_at wins. This is
 *    last-write-wins, chosen knowingly: the alternative (per-field merge) adds substantial
 *    complexity to protect against a scenario a single user with one phone does not hit.
 *  - COMPLETED SESSIONS ARE NEVER OVERWRITTEN LOCALLY BY A PULL. A finished workout is a
 *    record of something that physically happened; if the server disagrees with the device
 *    that recorded it, the device is right. This is the one place last-write-wins is
 *    explicitly rejected.
 */
class SyncEngine(
    private val context: Context,
    private val db: WinterArcDatabase,
    private val client: SupabaseClient,
) {
    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val LAST_SYNC = stringPreferencesKey("last_sync_iso")
    }

    sealed interface Outcome {
        data class Success(val pushed: Int, val at: Instant) : Outcome
        data class Skipped(val reason: String) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    val isConfigured: Boolean get() = client.isConfigured

    suspend fun signedInUserId(): String? =
        context.syncStore.data.first()[Keys.USER_ID]

    suspend fun signIn(email: String, password: String): Result<Unit> =
        when (val r = client.signIn(email, password)) {
            is SupabaseClient.SyncResult.Ok -> { store(r.value); Result.success(Unit) }
            is SupabaseClient.SyncResult.Err -> Result.failure(IllegalStateException(r.message))
        }

    suspend fun signUp(email: String, password: String): Result<Unit> =
        when (val r = client.signUp(email, password)) {
            is SupabaseClient.SyncResult.Ok -> { store(r.value); Result.success(Unit) }
            is SupabaseClient.SyncResult.Err -> Result.failure(IllegalStateException(r.message))
        }

    suspend fun signOut() {
        context.syncStore.edit { it.clear() }
    }

    private suspend fun store(s: SupabaseClient.Session) {
        context.syncStore.edit {
            it[Keys.ACCESS] = s.accessToken
            it[Keys.REFRESH] = s.refreshToken
            it[Keys.USER_ID] = s.userId
        }
    }

    /**
     * Pushes every locally-changed row. Returns an [Outcome] rather than throwing, because
     * "we are in a basement gym with no signal" is an ordinary state, not an error.
     */
    suspend fun sync(): Outcome {
        if (!client.isConfigured) return Outcome.Skipped("Cloud backup is not configured for this build.")
        val prefs = context.syncStore.data.first()
        val userId = prefs[Keys.USER_ID] ?: return Outcome.Skipped("Not signed in.")
        var token = prefs[Keys.ACCESS] ?: return Outcome.Skipped("Not signed in.")

        var pushed = 0
        var attemptedRefresh = false

        suspend fun push(table: String, rows: List<JsonObject>, mark: suspend (List<String>) -> Unit): String? {
            if (rows.isEmpty()) return null
            var result = client.upsert(table, rows, token)
            // An expired access token is the one failure worth retrying automatically.
            if (result is SupabaseClient.SyncResult.Err && result.code == 401 && !attemptedRefresh) {
                attemptedRefresh = true
                val refreshToken = prefs[Keys.REFRESH]
                if (refreshToken != null) {
                    when (val r = client.refresh(refreshToken)) {
                        is SupabaseClient.SyncResult.Ok -> {
                            store(r.value)
                            token = r.value.accessToken
                            result = client.upsert(table, rows, token)
                        }
                        is SupabaseClient.SyncResult.Err -> return r.message
                    }
                }
            }
            return when (result) {
                is SupabaseClient.SyncResult.Ok -> {
                    pushed += result.value
                    mark(rows.mapNotNull { (it["id"] as? JsonPrimitive)?.content })
                    null
                }
                is SupabaseClient.SyncResult.Err -> result.message
            }
        }

        val at = System.currentTimeMillis()

        // Pushed parent-first so a foreign key never arrives before the row it points at.
        val errors = buildList {
            push("exercises", db.exerciseDao().dirty().map { e ->
                row(userId) {
                    put("id", JsonPrimitive(e.id)); put("name", JsonPrimitive(e.name))
                    put("primary_muscle", JsonPrimitive(e.primaryMuscle))
                    put("equipment", JsonPrimitive(e.equipment))
                    put("default_rest_seconds", JsonPrimitive(e.defaultRestSeconds))
                    put("notes", e.notes.orNull()); put("is_custom", JsonPrimitive(e.isCustom))
                    put("is_archived", JsonPrimitive(e.isArchived))
                    put("deleted_at", e.deletedAt.isoOrNull())
                }
            }) { db.exerciseDao().markSynced(it, at) }?.let { add("exercises: $it") }

            push("programmes", db.programmeDao().dirty().map { p ->
                row(userId) {
                    put("id", JsonPrimitive(p.id)); put("name", JsonPrimitive(p.name))
                    put("description", p.description.orNull())
                    put("is_active", JsonPrimitive(p.isActive))
                    put("deleted_at", p.deletedAt.isoOrNull())
                }
            }) { db.programmeDao().markSynced(it, at) }?.let { add("programmes: $it") }

            push("workout_templates", db.templateDao().dirty().map { t ->
                row(userId) {
                    put("id", JsonPrimitive(t.id)); put("programme_id", JsonPrimitive(t.programmeId))
                    put("name", JsonPrimitive(t.name))
                    put("day_of_week", t.dayOfWeek?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("day_type", JsonPrimitive(t.dayType))
                    put("position", JsonPrimitive(t.position))
                    put("notes", t.notes.orNull())
                    put("is_archived", JsonPrimitive(t.isArchived))
                    put("deleted_at", t.deletedAt.isoOrNull())
                }
            }) { db.templateDao().markSynced(it, at) }?.let { add("templates: $it") }

            push("planned_exercises", db.plannedExerciseDao().dirty().map { p ->
                row(userId) {
                    put("id", JsonPrimitive(p.id)); put("template_id", JsonPrimitive(p.templateId))
                    put("exercise_id", JsonPrimitive(p.exerciseId))
                    put("position", JsonPrimitive(p.position))
                    put("superset_group", p.supersetGroup.orNull())
                    put("sets", JsonPrimitive(p.sets)); put("rep_low", JsonPrimitive(p.repLow))
                    put("rep_high", JsonPrimitive(p.repHigh))
                    put("target_weight_kg", p.targetWeightKg?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("rir", p.rir.orNull()); put("rest_seconds", JsonPrimitive(p.restSeconds))
                    put("style", JsonPrimitive(p.style)); put("notes", p.notes.orNull())
                    put("deleted_at", p.deletedAt.isoOrNull())
                }
            }) { db.plannedExerciseDao().markSynced(it, at) }?.let { add("planned: $it") }

            push("workout_sessions", db.sessionDao().dirty().map { s ->
                row(userId) {
                    put("id", JsonPrimitive(s.id))
                    put("template_id", s.templateId.orNull())
                    put("programme_id", s.programmeId.orNull())
                    put("name", JsonPrimitive(s.name))
                    put("session_date", JsonPrimitive(s.sessionDate))
                    put("started_at", JsonPrimitive(Instant.ofEpochSecond(s.startedAtEpochSec).toString()))
                    put("finished_at", s.finishedAtEpochSec?.let { JsonPrimitive(Instant.ofEpochSecond(it).toString()) } ?: JsonNull)
                    put("status", JsonPrimitive(s.status)); put("day_type", JsonPrimitive(s.dayType))
                    put("notes", s.notes.orNull())
                    put("deleted_at", s.deletedAt.isoOrNull())
                }
            }) { db.sessionDao().markSynced(it, at) }?.let { add("sessions: $it") }

            push("performed_exercises", db.performedExerciseDao().dirty().map { p ->
                row(userId) {
                    put("id", JsonPrimitive(p.id)); put("session_id", JsonPrimitive(p.sessionId))
                    put("exercise_id", JsonPrimitive(p.exerciseId))
                    put("position", JsonPrimitive(p.position))
                    put("superset_group", p.supersetGroup.orNull())
                    put("planned_sets", JsonPrimitive(p.plannedSets))
                    put("planned_rep_low", JsonPrimitive(p.plannedRepLow))
                    put("planned_rep_high", JsonPrimitive(p.plannedRepHigh))
                    put("planned_weight_kg", p.plannedWeightKg?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("planned_rest_seconds", JsonPrimitive(p.plannedRestSeconds))
                    put("planned_style", JsonPrimitive(p.plannedStyle))
                    put("replaced_exercise_id", p.replacedExerciseId.orNull())
                    put("is_ad_hoc", JsonPrimitive(p.isAdHoc))
                    put("is_skipped", JsonPrimitive(p.isSkipped))
                    put("notes", p.notes.orNull())
                    put("deleted_at", p.deletedAt.isoOrNull())
                }
            }) { db.performedExerciseDao().markSynced(it, at) }?.let { add("performed: $it") }

            push("actual_sets", db.actualSetDao().dirty().map { s ->
                row(userId) {
                    put("id", JsonPrimitive(s.id))
                    put("performed_exercise_id", JsonPrimitive(s.performedExerciseId))
                    put("set_number", JsonPrimitive(s.setNumber))
                    put("weight_kg", JsonPrimitive(s.weightKg)); put("reps", JsonPrimitive(s.reps))
                    put("is_warmup", JsonPrimitive(s.isWarmup))
                    put("rir", s.rir?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("notes", s.notes.orNull())
                    put("completed_at", s.completedAtEpochSec?.let { JsonPrimitive(Instant.ofEpochSecond(it).toString()) } ?: JsonNull)
                    put("deleted_at", s.deletedAt.isoOrNull())
                }
            }) { db.actualSetDao().markSynced(it, at) }?.let { add("sets: $it") }

            push("body_metrics", db.bodyMetricDao().dirty().map { m ->
                row(userId) {
                    put("id", JsonPrimitive(m.id)); put("metric_date", JsonPrimitive(m.metricDate))
                    put("weight_kg", m.weightKg?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("chest_cm", m.chestCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("waist_cm", m.waistCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("biceps_left_cm", m.bicepsLeftCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("biceps_right_cm", m.bicepsRightCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("shoulders_cm", m.shouldersCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("thigh_cm", m.thighCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("calf_cm", m.calfCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("neck_cm", m.neckCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("forearm_cm", m.forearmCm?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("body_fat_percent", m.bodyFatPercent?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("notes", m.notes.orNull())
                    put("deleted_at", m.deletedAt.isoOrNull())
                }
            }) { db.bodyMetricDao().markSynced(it, at) }?.let { add("body: $it") }
        }

        return if (errors.isEmpty()) {
            context.syncStore.edit { it[Keys.LAST_SYNC] = Instant.ofEpochMilli(at).toString() }
            Outcome.Success(pushed, Instant.ofEpochMilli(at))
        } else {
            Outcome.Failed(errors.first())
        }
    }

    suspend fun lastSync(): Instant? =
        context.syncStore.data.first()[Keys.LAST_SYNC]?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private inline fun row(userId: String, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        buildJsonObject {
            put("user_id", JsonPrimitive(userId))
            build()
        }

    private fun String?.orNull() = this?.let { JsonPrimitive(it) } ?: JsonNull
    private fun Long?.isoOrNull() = this?.let { JsonPrimitive(Instant.ofEpochMilli(it).toString()) } ?: JsonNull
}
