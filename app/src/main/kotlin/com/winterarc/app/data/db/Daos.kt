package com.winterarc.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY name")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    /**
     * Case-insensitive name lookup used to warn about duplicates before a custom exercise
     * is created. Duplicates are permitted — two gyms can legitimately have differently
     * loaded machines with the same name — but the user is told first.
     */
    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    @Upsert suspend fun upsert(e: ExerciseEntity)
    @Upsert suspend fun upsertAll(e: List<ExerciseEntity>)

    @Query("UPDATE exercises SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM exercises WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun dirty(): List<ExerciseEntity>

    @Query("UPDATE exercises SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}

@Dao
interface ProgrammeDao {
    @Query("SELECT * FROM programmes WHERE deletedAt IS NULL ORDER BY isActive DESC, name")
    fun observeAll(): Flow<List<ProgrammeEntity>>

    @Query("SELECT * FROM programmes WHERE isActive = 1 AND deletedAt IS NULL LIMIT 1")
    fun observeActive(): Flow<ProgrammeEntity?>

    @Query("SELECT * FROM programmes WHERE isActive = 1 AND deletedAt IS NULL LIMIT 1")
    suspend fun getActive(): ProgrammeEntity?

    @Query("SELECT COUNT(*) FROM programmes")
    suspend fun count(): Int

    @Upsert suspend fun upsert(p: ProgrammeEntity)
    @Upsert suspend fun upsertAll(p: List<ProgrammeEntity>)

    @Query("UPDATE programmes SET isActive = 0, updatedAt = :now")
    suspend fun clearActive(now: Long)

    @Query("UPDATE programmes SET isActive = 1, updatedAt = :now WHERE id = :id")
    suspend fun setActive(id: String, now: Long)

    @Transaction
    suspend fun makeActive(id: String, now: Long) {
        clearActive(now)
        setActive(id, now)
    }

    @Query("SELECT * FROM programmes WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun dirty(): List<ProgrammeEntity>

    @Query("UPDATE programmes SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_templates WHERE programmeId = :programmeId AND deletedAt IS NULL ORDER BY position")
    fun observeForProgramme(programmeId: String): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE deletedAt IS NULL ORDER BY position")
    fun observeAll(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getById(id: String): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_templates WHERE programmeId = :programmeId AND deletedAt IS NULL ORDER BY position")
    suspend fun getForProgramme(programmeId: String): List<WorkoutTemplateEntity>

    @Query(
        """
        SELECT * FROM workout_templates
        WHERE programmeId = :programmeId AND dayOfWeek = :dayOfWeek
          AND deletedAt IS NULL AND isArchived = 0
        ORDER BY position LIMIT 1
        """,
    )
    suspend fun getForDay(programmeId: String, dayOfWeek: Int): WorkoutTemplateEntity?

    @Upsert suspend fun upsert(t: WorkoutTemplateEntity)
    @Upsert suspend fun upsertAll(t: List<WorkoutTemplateEntity>)

    @Query("UPDATE workout_templates SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM workout_templates WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun dirty(): List<WorkoutTemplateEntity>

    @Query("UPDATE workout_templates SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}

@Dao
interface PlannedExerciseDao {
    @Query("SELECT * FROM planned_exercises WHERE templateId = :templateId AND deletedAt IS NULL ORDER BY position")
    fun observeForTemplate(templateId: String): Flow<List<PlannedExerciseEntity>>

    @Query("SELECT * FROM planned_exercises WHERE templateId = :templateId AND deletedAt IS NULL ORDER BY position")
    suspend fun getForTemplate(templateId: String): List<PlannedExerciseEntity>

    @Query("SELECT * FROM planned_exercises WHERE deletedAt IS NULL ORDER BY position")
    suspend fun getAll(): List<PlannedExerciseEntity>

    @Query("SELECT * FROM planned_exercises WHERE deletedAt IS NULL ORDER BY position")
    fun observeAllPlanned(): Flow<List<PlannedExerciseEntity>>

    @Upsert suspend fun upsert(p: PlannedExerciseEntity)
    @Upsert suspend fun upsertAll(p: List<PlannedExerciseEntity>)

    @Query("UPDATE planned_exercises SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM planned_exercises WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun dirty(): List<PlannedExerciseEntity>

    @Query("UPDATE planned_exercises SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM workout_sessions WHERE deletedAt IS NULL ORDER BY sessionDate DESC, startedAtEpochSec DESC")
    fun observeAll(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' AND deletedAt IS NULL ORDER BY sessionDate DESC")
    fun observeCompleted(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' AND deletedAt IS NULL ORDER BY startedAtEpochSec DESC LIMIT 1")
    fun observeInProgress(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' AND deletedAt IS NULL ORDER BY startedAtEpochSec DESC LIMIT 1")
    suspend fun getInProgress(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' AND deletedAt IS NULL")
    suspend fun getAllCompleted(): List<WorkoutSessionEntity>

    @Upsert suspend fun upsert(s: WorkoutSessionEntity)

    @Query("UPDATE workout_sessions SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM workout_sessions WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun dirty(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}

@Dao
interface PerformedExerciseDao {
    @Query("SELECT * FROM performed_exercises WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY position")
    suspend fun getForSession(sessionId: String): List<PerformedExerciseEntity>

    @Query("SELECT * FROM performed_exercises WHERE deletedAt IS NULL")
    suspend fun getAll(): List<PerformedExerciseEntity>

    @Upsert suspend fun upsert(p: PerformedExerciseEntity)
    @Upsert suspend fun upsertAll(p: List<PerformedExerciseEntity>)

    @Query("DELETE FROM performed_exercises WHERE sessionId = :sessionId AND id NOT IN (:keepIds)")
    suspend fun deleteRemoved(sessionId: String, keepIds: List<String>)

    @Query("DELETE FROM performed_exercises WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: String)

    @Query("SELECT * FROM performed_exercises WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun dirty(): List<PerformedExerciseEntity>

    @Query("UPDATE performed_exercises SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}

@Dao
interface ActualSetDao {
    @Query(
        """
        SELECT s.* FROM actual_sets s
        INNER JOIN performed_exercises p ON s.performedExerciseId = p.id
        WHERE p.sessionId = :sessionId AND s.deletedAt IS NULL
        ORDER BY s.setNumber
        """,
    )
    suspend fun getForSession(sessionId: String): List<ActualSetEntity>

    @Query("SELECT * FROM actual_sets WHERE deletedAt IS NULL")
    suspend fun getAll(): List<ActualSetEntity>

    @Upsert suspend fun upsertAll(s: List<ActualSetEntity>)

    @Query("DELETE FROM actual_sets WHERE performedExerciseId IN (:performedIds) AND id NOT IN (:keepIds)")
    suspend fun deleteRemoved(performedIds: List<String>, keepIds: List<String>)

    @Query("DELETE FROM actual_sets WHERE performedExerciseId IN (:performedIds)")
    suspend fun deleteAllFor(performedIds: List<String>)

    @Query("SELECT * FROM actual_sets WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun dirty(): List<ActualSetEntity>

    @Query("UPDATE actual_sets SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}

@Dao
interface BodyMetricDao {
    @Query("SELECT * FROM body_metrics WHERE deletedAt IS NULL ORDER BY metricDate DESC")
    fun observeAll(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics WHERE deletedAt IS NULL ORDER BY metricDate DESC")
    suspend fun getAll(): List<BodyMetricEntity>

    @Query("SELECT COUNT(*) FROM body_metrics")
    suspend fun count(): Int

    @Upsert suspend fun upsert(m: BodyMetricEntity)

    @Query("UPDATE body_metrics SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM body_metrics WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun dirty(): List<BodyMetricEntity>

    @Query("UPDATE body_metrics SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}
