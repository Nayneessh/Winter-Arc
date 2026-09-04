package com.winterarc.app.data.db

import com.winterarc.domain.model.ActualSet
import com.winterarc.domain.model.BodyMetric
import com.winterarc.domain.model.DayType
import com.winterarc.domain.model.Equipment
import com.winterarc.domain.model.Exercise
import com.winterarc.domain.model.MuscleGroup
import com.winterarc.domain.model.PerformedExercise
import com.winterarc.domain.model.PlannedExercise
import com.winterarc.domain.model.Programme
import com.winterarc.domain.model.SessionStatus
import com.winterarc.domain.model.TrainingStyle
import com.winterarc.domain.model.WorkoutSession
import com.winterarc.domain.model.WorkoutTemplate
import java.time.Instant
import java.time.LocalDate

/**
 * Entity <-> domain mapping.
 *
 * Enums are persisted by name and parsed defensively: an unrecognised value falls back to a
 * safe default rather than throwing. That matters because a row can arrive from a newer
 * version of the app via cloud sync, and a crash on read would be a far worse outcome than
 * showing one exercise as "Other".
 */

private inline fun <reified T : Enum<T>> parseEnum(value: String?, fallback: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

// --- Exercise ---

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    primaryMuscle = parseEnum(primaryMuscle, MuscleGroup.OTHER),
    equipment = parseEnum(equipment, Equipment.OTHER),
    defaultRestSeconds = defaultRestSeconds,
    notes = notes,
    isCustom = isCustom,
    isArchived = isArchived,
)

fun Exercise.toEntity(now: Long) = ExerciseEntity(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle.name,
    equipment = equipment.name,
    defaultRestSeconds = defaultRestSeconds,
    notes = notes,
    isCustom = isCustom,
    isArchived = isArchived,
    updatedAt = now,
)

// --- Programme / templates ---

fun ProgrammeEntity.toDomain(templates: List<WorkoutTemplate> = emptyList()) = Programme(
    id = id, name = name, description = description, isActive = isActive, templates = templates,
)

fun Programme.toEntity(now: Long) = ProgrammeEntity(
    id = id, name = name, description = description, isActive = isActive, updatedAt = now,
)

fun WorkoutTemplateEntity.toDomain(exercises: List<PlannedExercise> = emptyList()) = WorkoutTemplate(
    id = id,
    programmeId = programmeId,
    name = name,
    dayOfWeek = dayOfWeek,
    dayType = parseEnum(dayType, DayType.TRAINING),
    muscleGroups = muscleGroups
        .split(',')
        .filter { it.isNotBlank() }
        .map { parseEnum(it.trim(), MuscleGroup.OTHER) },
    position = position,
    notes = notes,
    exercises = exercises,
    isArchived = isArchived,
)

fun WorkoutTemplate.toEntity(now: Long) = WorkoutTemplateEntity(
    id = id,
    programmeId = programmeId,
    name = name,
    dayOfWeek = dayOfWeek,
    dayType = dayType.name,
    muscleGroups = muscleGroups.joinToString(",") { it.name },
    position = position,
    notes = notes,
    isArchived = isArchived,
    updatedAt = now,
)

fun PlannedExerciseEntity.toDomain() = PlannedExercise(
    id = id,
    exerciseId = exerciseId,
    position = position,
    supersetGroup = supersetGroup,
    sets = sets,
    repLow = repLow,
    repHigh = repHigh,
    targetWeightKg = targetWeightKg,
    rir = rir,
    restSeconds = restSeconds,
    style = parseEnum(style, TrainingStyle.UNSPECIFIED),
    notes = notes,
)

fun PlannedExercise.toEntity(templateId: String, now: Long) = PlannedExerciseEntity(
    id = id,
    templateId = templateId,
    exerciseId = exerciseId,
    position = position,
    supersetGroup = supersetGroup,
    sets = sets,
    repLow = repLow,
    repHigh = repHigh,
    targetWeightKg = targetWeightKg,
    rir = rir,
    restSeconds = restSeconds,
    style = style.name,
    notes = notes,
    updatedAt = now,
)

// --- Sessions ---

fun WorkoutSessionEntity.toDomain(exercises: List<PerformedExercise> = emptyList()) = WorkoutSession(
    id = id,
    templateId = templateId,
    programmeId = programmeId,
    name = name,
    date = LocalDate.parse(sessionDate),
    startedAt = Instant.ofEpochSecond(startedAtEpochSec),
    finishedAt = finishedAtEpochSec?.let { Instant.ofEpochSecond(it) },
    status = parseEnum(status, SessionStatus.IN_PROGRESS),
    dayType = parseEnum(dayType, DayType.TRAINING),
    notes = notes,
    exercises = exercises,
)

fun WorkoutSession.toEntity(now: Long) = WorkoutSessionEntity(
    id = id,
    templateId = templateId,
    programmeId = programmeId,
    name = name,
    sessionDate = date.toString(),
    startedAtEpochSec = startedAt.epochSecond,
    finishedAtEpochSec = finishedAt?.epochSecond,
    status = status.name,
    dayType = dayType.name,
    notes = notes,
    updatedAt = now,
)

fun PerformedExerciseEntity.toDomain(sets: List<ActualSet> = emptyList()) = PerformedExercise(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    position = position,
    supersetGroup = supersetGroup,
    plannedSets = plannedSets,
    plannedRepLow = plannedRepLow,
    plannedRepHigh = plannedRepHigh,
    plannedWeightKg = plannedWeightKg,
    plannedRestSeconds = plannedRestSeconds,
    plannedStyle = parseEnum(plannedStyle, TrainingStyle.UNSPECIFIED),
    replacedExerciseId = replacedExerciseId,
    isAdHoc = isAdHoc,
    isSkipped = isSkipped,
    notes = notes,
    sets = sets,
)

fun PerformedExercise.toEntity(now: Long) = PerformedExerciseEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    position = position,
    supersetGroup = supersetGroup,
    plannedSets = plannedSets,
    plannedRepLow = plannedRepLow,
    plannedRepHigh = plannedRepHigh,
    plannedWeightKg = plannedWeightKg,
    plannedRestSeconds = plannedRestSeconds,
    plannedStyle = plannedStyle.name,
    replacedExerciseId = replacedExerciseId,
    isAdHoc = isAdHoc,
    isSkipped = isSkipped,
    notes = notes,
    updatedAt = now,
)

fun ActualSetEntity.toDomain() = ActualSet(
    id = id,
    performedExerciseId = performedExerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isWarmup = isWarmup,
    rir = rir,
    notes = notes,
    completedAt = completedAtEpochSec?.let { Instant.ofEpochSecond(it) },
)

fun ActualSet.toEntity(now: Long) = ActualSetEntity(
    id = id,
    performedExerciseId = performedExerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isWarmup = isWarmup,
    rir = rir,
    notes = notes,
    completedAtEpochSec = completedAt?.epochSecond,
    updatedAt = now,
)

// --- Body ---

fun BodyMetricEntity.toDomain() = BodyMetric(
    id = id,
    date = LocalDate.parse(metricDate),
    weightKg = weightKg,
    chestCm = chestCm,
    waistCm = waistCm,
    bicepsLeftCm = bicepsLeftCm,
    bicepsRightCm = bicepsRightCm,
    shouldersCm = shouldersCm,
    thighCm = thighCm,
    calfCm = calfCm,
    neckCm = neckCm,
    forearmCm = forearmCm,
    bodyFatPercent = bodyFatPercent,
    notes = notes,
)

fun BodyMetric.toEntity(now: Long) = BodyMetricEntity(
    id = id,
    metricDate = date.toString(),
    weightKg = weightKg,
    chestCm = chestCm,
    waistCm = waistCm,
    bicepsLeftCm = bicepsLeftCm,
    bicepsRightCm = bicepsRightCm,
    shouldersCm = shouldersCm,
    thighCm = thighCm,
    calfCm = calfCm,
    neckCm = neckCm,
    forearmCm = forearmCm,
    bodyFatPercent = bodyFatPercent,
    notes = notes,
    updatedAt = now,
)
