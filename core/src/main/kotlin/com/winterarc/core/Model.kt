@file:UseSerializers(LocalDateSerializer::class, DayOfWeekSerializer::class)

package com.winterarc.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

// ---------------------------------------------------------------------------------------------
// Serializers for the two java.time types the model uses.
//
// Both are stored in their ISO / canonical text form rather than as numbers. A saved file is
// meant to be readable and hand-fixable: "2026-09-07" survives inspection, an epoch day does not.
// ---------------------------------------------------------------------------------------------

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.DayOfWeek", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DayOfWeek) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): DayOfWeek = DayOfWeek.valueOf(decoder.decodeString())
}

fun newId(): String = UUID.randomUUID().toString()

// ---------------------------------------------------------------------------------------------
// Taxonomy
// ---------------------------------------------------------------------------------------------

/**
 * Primary muscle worked. This is the axis the dashboard splits volume by, so it is deliberately
 * coarse: fine-grained heads (long head vs lateral head) live in the exercise's own detail line,
 * where they inform the lifter without fragmenting the chart into unreadable slivers.
 */
@Serializable
enum class Muscle(val display: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    CORE("Core"),
    CONDITIONING("Conditioning"),
    OTHER("Other"),
}

@Serializable
enum class Equipment(val display: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    MACHINE("Machine"),
    CABLE("Cable"),
    BODYWEIGHT("Bodyweight"),
    KETTLEBELL("Kettlebell"),
    BAND("Band"),
    OTHER("Other"),
}

/** Programme-level emphasis. Mirrors the star/diamond marking in the source plan. */
@Serializable
enum class Priority(val display: String, val symbol: String) {
    NONE("", ""),
    ARMS("Arm priority", "★"),
    BACK("Back density", "◆"),
}

/** Colour identity for a routine, so each training day is recognisable at a glance. */
@Serializable
enum class Accent { GOLD, GREEN, BLUE }

@Serializable
enum class WeightUnit(val display: String, val suffix: String) {
    KG("Kilograms", "kg"),
    LB("Pounds", "lb"),
}

// ---------------------------------------------------------------------------------------------
// Catalogue
// ---------------------------------------------------------------------------------------------

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val muscle: Muscle,
    /** The specific head or emphasis, e.g. "Triceps long head". Shown under the name. */
    val detail: String = "",
    val equipment: Equipment = Equipment.OTHER,
    /** Bodyweight movements log reps against added load, which may legitimately be zero. */
    val bodyweight: Boolean = false,
    /** True for anything the user created, so it can be told apart from the seeded catalogue. */
    val custom: Boolean = false,
    val archived: Boolean = false,
)

// ---------------------------------------------------------------------------------------------
// Programme -- what is meant to happen
// ---------------------------------------------------------------------------------------------

/**
 * One prescribed movement inside a routine.
 *
 * [group] carries the pairing code from the plan ("A1", "B2"). Items sharing a letter are a
 * superset and are drawn as one connected block. The number orders them within it.
 */
@Serializable
data class PlanItem(
    val id: String = newId(),
    val exerciseId: String,
    val group: String,
    val sets: Int,
    val repLow: Int,
    val repHigh: Int,
    val rir: String = "",
    val restSeconds: Int = 90,
    val style: String = "",
    val cue: String = "",
    val priority: Priority = Priority.NONE,
    /** Optional working weight carried by the plan. Null means "use last time's". */
    val targetWeightKg: Double? = null,
) {
    /** "A1" -> "A". Items with the same letter are supersetted together. */
    val groupLetter: String get() = group.takeWhile { it.isLetter() }.ifEmpty { group }

    val repRange: String get() = if (repLow == repHigh) "$repLow" else "$repLow-$repHigh"
}

@Serializable
data class Routine(
    val id: String = newId(),
    val name: String,
    val subtitle: String = "",
    val accent: Accent = Accent.GREEN,
    /** Days this routine is scheduled on. Empty means it is run on demand only. */
    val days: List<DayOfWeek> = emptyList(),
    val items: List<PlanItem> = emptyList(),
    val estimatedMinutes: Int = 60,
    /** A note shown before the session starts, e.g. "do AFTER MMA, never before". */
    val note: String = "",
    val archived: Boolean = false,
) {
    val totalSets: Int get() = items.sumOf { it.sets }
}

@Serializable
data class Programme(
    val id: String = newId(),
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val routines: List<Routine> = emptyList(),
    val active: Boolean = true,
) {
    fun routineFor(day: DayOfWeek): Routine? =
        routines.firstOrNull { !it.archived && day in it.days }
}

// ---------------------------------------------------------------------------------------------
// Sessions -- what actually happened
//
// A session is a snapshot, never a live reference to the plan. Editing the programme in March
// must not rewrite what was lifted in January, so every prescription is copied in at start.
// ---------------------------------------------------------------------------------------------

@Serializable
data class SetEntry(
    val id: String = newId(),
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val done: Boolean = false,
    /** Warm-ups are logged but excluded from volume, tonnage and personal records. */
    val warmup: Boolean = false,
    val rpe: Double? = null,
) {
    /** Volume only counts a completed working set that actually moved a load for reps. */
    val volumeKg: Double get() = if (done && !warmup) weightKg * reps else 0.0

    val counts: Boolean get() = done && !warmup && reps > 0
}

@Serializable
data class SessionExercise(
    val id: String = newId(),
    val exerciseId: String,
    val group: String = "",
    val plannedSets: Int = 0,
    val repLow: Int = 0,
    val repHigh: Int = 0,
    val rir: String = "",
    val restSeconds: Int = 90,
    val style: String = "",
    val cue: String = "",
    val priority: Priority = Priority.NONE,
    val sets: List<SetEntry> = emptyList(),
    val skipped: Boolean = false,
    val note: String = "",
) {
    val groupLetter: String get() = group.takeWhile { it.isLetter() }.ifEmpty { group }
    val repRange: String get() = if (repLow == repHigh) "$repLow" else "$repLow-$repHigh"

    val workingSets: List<SetEntry> get() = sets.filter { it.counts }
    val completedCount: Int get() = sets.count { it.done }
    val volumeKg: Double get() = sets.sumOf { it.volumeKg }
    val totalReps: Int get() = workingSets.sumOf { it.reps }
    val topWeightKg: Double get() = workingSets.maxOfOrNull { it.weightKg } ?: 0.0

    /** Sets performed beyond what was prescribed. Never negative. */
    val extraSets: Int get() = (workingSets.size - plannedSets).coerceAtLeast(0)

    val isComplete: Boolean get() = skipped || (sets.isNotEmpty() && sets.all { it.done })
}

@Serializable
data class Session(
    val id: String = newId(),
    val date: LocalDate,
    val startedAtMillis: Long = 0L,
    val finishedAtMillis: Long? = null,
    val routineId: String? = null,
    val title: String = "",
    val accent: Accent = Accent.GREEN,
    val exercises: List<SessionExercise> = emptyList(),
    val note: String = "",
    val bodyweightKg: Double? = null,
) {
    val finished: Boolean get() = finishedAtMillis != null

    val volumeKg: Double get() = exercises.sumOf { it.volumeKg }
    val totalReps: Int get() = exercises.sumOf { it.totalReps }
    val workingSetCount: Int get() = exercises.sumOf { it.workingSets.size }
    val plannedSetCount: Int get() = exercises.sumOf { it.plannedSets }
    val extraSets: Int get() = exercises.sumOf { it.extraSets }

    val durationMinutes: Int?
        get() = finishedAtMillis?.let { end ->
            if (startedAtMillis <= 0L) null
            else ((end - startedAtMillis) / 60_000L).toInt().coerceAtLeast(0)
        }

    /** Fraction of prescribed sets actually completed, clamped to 0..1 for progress rings. */
    val completion: Double
        get() {
            val target = exercises.sumOf { maxOf(it.plannedSets, 1) }
            if (target == 0) return 0.0
            return (exercises.sumOf { minOf(it.completedCount, maxOf(it.plannedSets, 1)) }.toDouble() / target)
                .coerceIn(0.0, 1.0)
        }
}

// ---------------------------------------------------------------------------------------------
// Body
// ---------------------------------------------------------------------------------------------

/**
 * The measurement sites tracked by default. Stored by key so a user-added site never collides
 * with a built-in one and nothing is lost if the built-in list changes later.
 */
object MeasurementSites {
    const val NECK = "neck"
    const val CHEST = "chest"
    const val WAIST = "waist"
    const val HIPS = "hips"
    const val ARM_LEFT = "arm_l"
    const val ARM_RIGHT = "arm_r"
    const val FOREARM = "forearm"
    const val THIGH = "thigh"
    const val CALF = "calf"

    /** Ordered for display: torso top to bottom, then limbs. */
    val ordered: List<Pair<String, String>> = listOf(
        NECK to "Neck",
        CHEST to "Chest",
        WAIST to "Waist",
        HIPS to "Hips",
        ARM_LEFT to "Left arm",
        ARM_RIGHT to "Right arm",
        FOREARM to "Forearm",
        THIGH to "Thigh",
        CALF to "Calf",
    )

    fun label(key: String): String =
        ordered.firstOrNull { it.first == key }?.second
            ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Serializable
data class BodyEntry(
    val id: String = newId(),
    val date: LocalDate,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    /** Site key -> centimetres. Absent means "not measured", which is not the same as zero. */
    val measurementsCm: Map<String, Double> = emptyMap(),
    val note: String = "",
) {
    /** Lean mass in kg, only when both inputs exist. Never inferred from one of them. */
    val leanMassKg: Double?
        get() {
            val w = weightKg ?: return null
            val bf = bodyFatPct ?: return null
            return w * (1.0 - bf / 100.0)
        }

    val fatMassKg: Double?
        get() {
            val w = weightKg ?: return null
            val bf = bodyFatPct ?: return null
            return w * (bf / 100.0)
        }
}

// ---------------------------------------------------------------------------------------------
// Goals
// ---------------------------------------------------------------------------------------------

/**
 * A strength target with an intermediate milestone, matching how the source plan was written:
 * a current load, a 12-week checkpoint, and an end goal.
 */
@Serializable
data class LiftGoal(
    val id: String = newId(),
    val exerciseId: String,
    val label: String = "",
    val startKg: Double,
    val milestoneKg: Double,
    val targetKg: Double,
)

@Serializable
data class Goals(
    val startWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val startBodyFatPct: Double? = null,
    val targetBodyFatPct: Double? = null,
    val weeklySessionTarget: Int = 6,
    val liftGoals: List<LiftGoal> = emptyList(),
)

// ---------------------------------------------------------------------------------------------
// Preferences
// ---------------------------------------------------------------------------------------------

@Serializable
data class Prefs(
    val unit: WeightUnit = WeightUnit.KG,
    /** Increment used by the weight stepper, in kilograms. */
    val weightStepKg: Double = 2.5,
    val restTimerAutoStart: Boolean = true,
    val keepScreenOn: Boolean = true,
    val soundOnRestEnd: Boolean = true,
)
