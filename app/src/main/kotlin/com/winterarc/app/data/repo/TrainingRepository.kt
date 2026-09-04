package com.winterarc.app.data.repo

import androidx.room.withTransaction
import com.winterarc.app.data.db.*
import com.winterarc.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The single gateway between the UI and storage.
 *
 * Session writes go through [saveSession], which persists the entire session graph
 * (session -> performed exercises -> sets) inside ONE transaction. That is deliberate:
 * a workout is only meaningful as a whole, and a process death midway through a partial
 * write would otherwise leave sets attached to an exercise that does not exist.
 */
class TrainingRepository(
    private val db: WinterArcDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val exerciseDao = db.exerciseDao()
    private val programmeDao = db.programmeDao()
    private val templateDao = db.templateDao()
    private val plannedDao = db.plannedExerciseDao()
    private val sessionDao = db.sessionDao()
    private val performedDao = db.performedExerciseDao()
    private val setDao = db.actualSetDao()
    private val bodyDao = db.bodyMetricDao()

    // -----------------------------------------------------------------------
    // Exercise library
    // -----------------------------------------------------------------------

    fun observeExercises(): Flow<List<Exercise>> =
        exerciseDao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun allExercises(): List<Exercise> = exerciseDao.getAll().map { it.toDomain() }

    suspend fun exerciseById(id: String): Exercise? = exerciseDao.getById(id)?.toDomain()

    /** Returns an existing exercise with the same name, so the UI can warn before creating a duplicate. */
    suspend fun findExerciseByName(name: String): Exercise? =
        exerciseDao.findByName(name.trim())?.toDomain()

    suspend fun saveExercise(exercise: Exercise) = exerciseDao.upsert(exercise.toEntity(now()))

    suspend fun archiveExercise(id: String) {
        val e = exerciseDao.getById(id) ?: return
        exerciseDao.upsert(e.copy(isArchived = true, updatedAt = now()))
    }

    // -----------------------------------------------------------------------
    // Programme and templates (the PLAN)
    // -----------------------------------------------------------------------

    fun observeProgrammes(): Flow<List<Programme>> =
        programmeDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveProgramme(): Flow<Programme?> =
        programmeDao.observeActive().map { it?.toDomain() }

    suspend fun activeProgramme(): Programme? = programmeDao.getActive()?.toDomain()

    suspend fun setActiveProgramme(id: String) = programmeDao.makeActive(id, now())

    fun observeTemplates(programmeId: String): Flow<List<WorkoutTemplate>> =
        combine(
            templateDao.observeForProgramme(programmeId),
            plannedDao.observeForTemplateAll(),
        ) { templates, planned ->
            val byTemplate = planned.groupBy { it.templateId }
            templates.map { t ->
                t.toDomain((byTemplate[t.id] ?: emptyList()).sortedBy { it.position }.map { it.toDomain() })
            }
        }

    /** One-shot equivalent of [observeTemplates], for screens that load rather than observe. */
    suspend fun observeTemplatesOnce(programmeId: String): List<WorkoutTemplate> {
        val templates = db.templateDao().getForProgramme(programmeId)
        val planned = plannedDao.getAll().groupBy { it.templateId }
        return templates.map { t ->
            t.toDomain((planned[t.id] ?: emptyList()).sortedBy { it.position }.map { it.toDomain() })
        }
    }

    suspend fun templateWithExercises(templateId: String): WorkoutTemplate? {
        val t = templateDao.getById(templateId) ?: return null
        val planned = plannedDao.getForTemplate(templateId).map { it.toDomain() }
        return t.toDomain(planned)
    }

    /** Today's prescribed workout, or null when the day is rest, MMA or unassigned. */
    suspend fun templateForDate(date: LocalDate): WorkoutTemplate? {
        val programme = programmeDao.getActive() ?: return null
        val t = templateDao.getForDay(programme.id, date.dayOfWeek.value) ?: return null
        return templateWithExercises(t.id)
    }

    suspend fun saveTemplate(template: WorkoutTemplate) {
        val ts = now()
        db.withTransaction {
            templateDao.upsert(template.toEntity(ts))
            val existing = plannedDao.getForTemplate(template.id).map { it.id }.toSet()
            val incoming = template.exercises.map { it.id }.toSet()
            // Entries removed from the plan are soft-deleted so the removal reaches the cloud.
            (existing - incoming).forEach { plannedDao.softDelete(it, ts) }
            plannedDao.upsertAll(template.exercises.map { it.toEntity(template.id, ts) })
        }
    }

    suspend fun deleteTemplate(id: String) = templateDao.softDelete(id, now())

    suspend fun saveProgramme(programme: Programme) {
        val ts = now()
        db.withTransaction {
            programmeDao.upsert(programme.toEntity(ts))
            programme.templates.forEach { t ->
                templateDao.upsert(t.toEntity(ts))
                plannedDao.upsertAll(t.exercises.map { it.toEntity(t.id, ts) })
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sessions (the ACTUAL)
    // -----------------------------------------------------------------------

    fun observeInProgressSession(): Flow<WorkoutSession?> =
        sessionDao.observeInProgress().map { it?.toDomain() }

    suspend fun inProgressSessionId(): String? = sessionDao.getInProgress()?.id

    /** Loads a session with its full graph of exercises and sets. */
    suspend fun loadSession(id: String): WorkoutSession? {
        val s = sessionDao.getById(id) ?: return null
        val performed = performedDao.getForSession(id)
        val sets = setDao.getForSession(id).groupBy { it.performedExerciseId }
        return s.toDomain(
            performed
                .sortedBy { it.position }
                .map { pe ->
                    pe.toDomain((sets[pe.id] ?: emptyList()).sortedBy { it.setNumber }.map { it.toDomain() })
                },
        )
    }

    /**
     * Persists the whole session graph atomically, reconciling deletions.
     *
     * Rows that vanished from the in-memory session (a removed exercise, a deleted set) are
     * hard-deleted here rather than soft-deleted, because a session that is still in progress
     * has never been pushed as a finished record; there is nothing in the cloud to tombstone.
     * Once a session is COMPLETED the app freezes it, so this path stops mutating it.
     */
    suspend fun saveSession(session: WorkoutSession) {
        val ts = now()
        db.withTransaction {
            sessionDao.upsert(session.toEntity(ts))

            val performedIds = session.exercises.map { it.id }
            if (performedIds.isEmpty()) {
                performedDao.deleteAllForSession(session.id)
            } else {
                performedDao.deleteRemoved(session.id, performedIds)
                performedDao.upsertAll(session.exercises.map { it.toEntity(ts) })
            }

            val allSets = session.exercises.flatMap { it.sets }
            if (performedIds.isNotEmpty()) {
                if (allSets.isEmpty()) {
                    setDao.deleteAllFor(performedIds)
                } else {
                    setDao.deleteRemoved(performedIds, allSets.map { it.id })
                    setDao.upsertAll(allSets.map { it.toEntity(ts) })
                }
            }
        }
    }

    suspend fun deleteSession(id: String) = sessionDao.softDelete(id, now())

    fun observeCompletedSessions(): Flow<List<WorkoutSession>> =
        sessionDao.observeCompleted().map { list -> list.map { it.toDomain() } }

    /**
     * Every completed session with its full graph. Analytics needs the sets, not just the
     * session headers, so this assembles the whole history in three queries rather than
     * one query per session.
     */
    suspend fun completedHistory(): List<WorkoutSession> {
        val sessions = sessionDao.getAllCompleted()
        if (sessions.isEmpty()) return emptyList()
        val performed = performedDao.getAll().groupBy { it.sessionId }
        val sets = setDao.getAll().groupBy { it.performedExerciseId }
        return sessions.map { s ->
            s.toDomain(
                (performed[s.id] ?: emptyList())
                    .sortedBy { it.position }
                    .map { pe ->
                        pe.toDomain((sets[pe.id] ?: emptyList()).sortedBy { it.setNumber }.map { it.toDomain() })
                    },
            )
        }
    }

    /** The most recent completed performance of a movement, used for the "last time" hint. */
    suspend fun lastPerformance(exerciseId: String, beforeSessionId: String? = null): PerformedExercise? =
        completedHistory()
            .filter { it.id != beforeSessionId }
            .sortedByDescending { it.date }
            .firstNotNullOfOrNull { s ->
                s.performedExercises.lastOrNull { it.exerciseId == exerciseId && it.workingSets.isNotEmpty() }
            }

    // -----------------------------------------------------------------------
    // Body metrics
    // -----------------------------------------------------------------------

    fun observeBodyMetrics(): Flow<List<BodyMetric>> =
        bodyDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun allBodyMetrics(): List<BodyMetric> = bodyDao.getAll().map { it.toDomain() }

    suspend fun saveBodyMetric(metric: BodyMetric) = bodyDao.upsert(metric.toEntity(now()))

    suspend fun deleteBodyMetric(id: String) = bodyDao.softDelete(id, now())

    // -----------------------------------------------------------------------
    // Destructive
    // -----------------------------------------------------------------------

    /** Wipes every table. Guarded by an explicit confirmation in Settings. */
    suspend fun deleteAllData() {
        db.withTransaction { db.clearAllTables() }
    }
}

/** Observes planned exercises across all templates so the template list can be assembled reactively. */
private fun PlannedExerciseDao.observeForTemplateAll(): Flow<List<PlannedExerciseEntity>> =
    observeAllPlanned()
