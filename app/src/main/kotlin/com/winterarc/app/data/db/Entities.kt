package com.winterarc.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities.
 *
 * Every row carries three pieces of sync metadata:
 *  - updatedAt  epoch millis of the last local change
 *  - syncedAt   epoch millis of the last successful push, or null if never pushed
 *  - deletedAt  soft-delete marker, so a delete made offline still propagates
 *
 * A row is "dirty" when syncedAt is null or older than updatedAt. That single rule drives
 * the whole push queue without a separate outbox table to keep consistent.
 *
 * Ids are client-generated UUID strings, identical to the ids used in Supabase, so a row
 * inserted offline keeps its identity forever and can never collide on upload.
 */

@Entity(
    tableName = "exercises",
    indices = [Index("name"), Index("primaryMuscle")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: String,
    val equipment: String,
    val defaultRestSeconds: Int,
    val notes: String?,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val deletedAt: Long? = null,
)

@Entity(tableName = "programmes")
data class ProgrammeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val isActive: Boolean,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "workout_templates",
    foreignKeys = [
        ForeignKey(
            entity = ProgrammeEntity::class,
            parentColumns = ["id"],
            childColumns = ["programmeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("programmeId"), Index("dayOfWeek")],
)
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val programmeId: String,
    val name: String,
    val dayOfWeek: Int?,
    val dayType: String,
    val muscleGroups: String,
    val position: Int,
    val notes: String?,
    val isArchived: Boolean,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "planned_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class PlannedExerciseEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    val supersetGroup: String?,
    val sets: Int,
    val repLow: Int,
    val repHigh: Int,
    val targetWeightKg: Double?,
    val rir: String?,
    val restSeconds: Int,
    val style: String,
    val notes: String?,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index("sessionDate"), Index("status"), Index("templateId")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val templateId: String?,
    val programmeId: String?,
    val name: String,
    /** ISO-8601 local date, stored as text so it sorts lexicographically. */
    val sessionDate: String,
    val startedAtEpochSec: Long,
    val finishedAtEpochSec: Long?,
    val status: String,
    val dayType: String,
    val notes: String?,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "performed_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class PerformedExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val supersetGroup: String?,
    // Snapshot of the plan as it stood when the session began.
    val plannedSets: Int,
    val plannedRepLow: Int,
    val plannedRepHigh: Int,
    val plannedWeightKg: Double?,
    val plannedRestSeconds: Int,
    val plannedStyle: String,
    val replacedExerciseId: String?,
    val isAdHoc: Boolean,
    val isSkipped: Boolean,
    val notes: String?,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "actual_sets",
    foreignKeys = [
        ForeignKey(
            entity = PerformedExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["performedExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("performedExerciseId")],
)
data class ActualSetEntity(
    @PrimaryKey val id: String,
    val performedExerciseId: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean,
    val rir: Int?,
    val notes: String?,
    val completedAtEpochSec: Long?,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val deletedAt: Long? = null,
)

@Entity(tableName = "body_metrics", indices = [Index("metricDate")])
data class BodyMetricEntity(
    @PrimaryKey val id: String,
    val metricDate: String,
    val weightKg: Double?,
    val chestCm: Double?,
    val waistCm: Double?,
    val bicepsLeftCm: Double?,
    val bicepsRightCm: Double?,
    val shouldersCm: Double?,
    val thighCm: Double?,
    val calfCm: Double?,
    val neckCm: Double?,
    val forearmCm: Double?,
    val bodyFatPercent: Double?,
    val notes: String?,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val deletedAt: Long? = null,
)
