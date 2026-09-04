package com.winterarc.app

import com.google.common.truth.Truth.assertThat
import com.winterarc.app.data.db.*
import com.winterarc.domain.model.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Round-trip tests for the entity <-> domain mapping.
 *
 * These exist because a mapper is exactly the kind of code where a copy-paste slip silently
 * swaps two fields — writing waist into chest, or planned reps into actual — and nothing
 * fails loudly. It would surface months later as history that quietly disagrees with what
 * was performed. Round-tripping every field catches that at build time.
 *
 * Room entities are plain data classes, so this runs on the JVM with no device.
 */
class MappersTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `exercise round-trips every field`() {
        val original = Exercise(
            id = "ex-1",
            name = "Incline Barbell Press",
            primaryMuscle = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            defaultRestSeconds = 180,
            notes = "Upper-chest mass anchor.",
            isCustom = true,
            isArchived = false,
        )

        assertThat(original.toEntity(now).toDomain()).isEqualTo(original)
    }

    @Test
    fun `planned exercise round-trips including superset and rep range`() {
        val original = PlannedExercise(
            id = "p-1",
            exerciseId = "ex-1",
            position = 3,
            supersetGroup = "B",
            sets = 4,
            repLow = 5,
            repHigh = 8,
            targetWeightKg = 57.5,
            rir = "1-2",
            restSeconds = 150,
            style = TrainingStyle.HEAVY_STRENGTH,
            notes = "Double progression.",
        )

        assertThat(original.toEntity("tpl-1", now).toDomain()).isEqualTo(original)
    }

    @Test
    fun `template round-trips muscle groups through their joined representation`() {
        val original = WorkoutTemplate(
            id = "tpl-1",
            programmeId = "prog-1",
            name = "Upper Push + Arms",
            dayOfWeek = 2,
            dayType = DayType.TRAINING,
            muscleGroups = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
            position = 0,
            notes = "Follows Monday MMA.",
            isArchived = false,
        )

        val restored = original.toEntity(now).toDomain()

        assertThat(restored.muscleGroups).containsExactlyElementsIn(original.muscleGroups).inOrder()
        assertThat(restored.copy(exercises = emptyList())).isEqualTo(original)
    }

    @Test
    fun `a template with no muscle groups does not round-trip into a phantom entry`() {
        val original = WorkoutTemplate(
            id = "tpl-rest",
            programmeId = "prog-1",
            name = "Rest",
            dayOfWeek = 7,
            dayType = DayType.REST,
            muscleGroups = emptyList(),
        )

        // Naive split("," ) on an empty string yields [""], which would map to OTHER.
        assertThat(original.toEntity(now).toDomain().muscleGroups).isEmpty()
    }

    @Test
    fun `session round-trips dates and instants without drift`() {
        val original = WorkoutSession(
            id = "s-1",
            templateId = "tpl-1",
            programmeId = "prog-1",
            name = "Upper Push + Arms",
            date = LocalDate.of(2026, 9, 4),
            startedAt = Instant.parse("2026-09-04T09:15:30Z"),
            finishedAt = Instant.parse("2026-09-04T10:37:00Z"),
            status = SessionStatus.COMPLETED,
            dayType = DayType.TRAINING,
            notes = "Felt strong.",
        )

        val restored = original.toEntity(now).toDomain()

        assertThat(restored.date).isEqualTo(original.date)
        assertThat(restored.startedAt).isEqualTo(original.startedAt)
        assertThat(restored.finishedAt).isEqualTo(original.finishedAt)
        assertThat(restored.status).isEqualTo(SessionStatus.COMPLETED)
    }

    @Test
    fun `an unfinished session round-trips with a null finish time`() {
        val original = WorkoutSession(
            id = "s-2",
            templateId = null,
            programmeId = null,
            name = "Custom workout",
            date = LocalDate.of(2026, 9, 4),
            startedAt = Instant.parse("2026-09-04T09:15:30Z"),
            finishedAt = null,
            status = SessionStatus.IN_PROGRESS,
        )

        val restored = original.toEntity(now).toDomain()

        assertThat(restored.finishedAt).isNull()
        assertThat(restored.templateId).isNull()
        assertThat(restored.durationSeconds).isNull()
    }

    /** The plan snapshot is what makes history immutable; every field of it must survive. */
    @Test
    fun `performed exercise round-trips the whole plan snapshot and its provenance`() {
        val original = PerformedExercise(
            id = "pe-1",
            sessionId = "s-1",
            exerciseId = "ex-db-press",
            position = 1,
            supersetGroup = "A",
            plannedSets = 3,
            plannedRepLow = 8,
            plannedRepHigh = 10,
            plannedWeightKg = 27.5,
            plannedRestSeconds = 150,
            plannedStyle = TrainingStyle.HYPERTROPHY,
            replacedExerciseId = "ex-incline-press",
            isAdHoc = false,
            isSkipped = false,
            notes = "Swapped, bench was taken.",
        )

        assertThat(original.toEntity(now).toDomain()).isEqualTo(original)
    }

    @Test
    fun `actual set round-trips decimal weight and warm-up flag`() {
        val original = ActualSet(
            id = "set-1",
            performedExerciseId = "pe-1",
            setNumber = 2,
            weightKg = 17.5,
            reps = 9,
            isWarmup = true,
            rir = 2,
            notes = "Slow eccentric.",
            completedAt = Instant.parse("2026-09-04T09:31:00Z"),
        )

        assertThat(original.toEntity(now).toDomain()).isEqualTo(original)
    }

    /**
     * The measurement fields are the highest-risk mapping in the app: eleven similarly-typed
     * nullable doubles in a row. Each gets a distinct value so a swap cannot pass.
     */
    @Test
    fun `body metric round-trips every measurement to its own field`() {
        val original = BodyMetric(
            id = "m-1",
            date = LocalDate.of(2026, 9, 4),
            weightKg = 80.5,
            chestCm = 106.68,
            waistCm = 96.52,
            bicepsLeftCm = 40.64,
            bicepsRightCm = 41.10,
            shouldersCm = 120.0,
            thighCm = 58.5,
            calfCm = 38.2,
            neckCm = 39.4,
            forearmCm = 29.7,
            bodyFatPercent = 24.5,
            notes = "Baseline",
        )

        assertThat(original.toEntity(now).toDomain()).isEqualTo(original)
    }

    @Test
    fun `unmeasured fields stay null rather than becoming zero`() {
        val original = BodyMetric(
            id = "m-2",
            date = LocalDate.of(2026, 9, 11),
            weightKg = 79.8,
        )

        val restored = original.toEntity(now).toDomain()

        assertThat(restored.weightKg).isEqualTo(79.8)
        assertThat(restored.chestCm).isNull()
        assertThat(restored.bodyFatPercent).isNull()
        assertThat(restored.fatMassKg).isNull()
    }

    /**
     * Rows can arrive from a newer build of the app through cloud sync. An unknown enum must
     * degrade to a safe default, never crash the read.
     */
    @Test
    fun `an unrecognised enum value falls back instead of throwing`() {
        val entity = ExerciseEntity(
            id = "ex-future",
            name = "Something New",
            primaryMuscle = "ADDUCTORS_FROM_A_LATER_VERSION",
            equipment = "SLED",
            defaultRestSeconds = 90,
            notes = null,
            isCustom = true,
            isArchived = false,
            updatedAt = now,
        )

        val restored = entity.toDomain()

        assertThat(restored.primaryMuscle).isEqualTo(MuscleGroup.OTHER)
        assertThat(restored.equipment).isEqualTo(Equipment.OTHER)
        assertThat(restored.name).isEqualTo("Something New")
    }

    @Test
    fun `sync metadata starts unsynced so a new row is dirty`() {
        val entity = Exercise(
            id = "ex-1",
            name = "Test",
            primaryMuscle = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
        ).toEntity(now)

        assertThat(entity.updatedAt).isEqualTo(now)
        assertThat(entity.syncedAt).isNull()
        assertThat(entity.deletedAt).isNull()
    }
}
