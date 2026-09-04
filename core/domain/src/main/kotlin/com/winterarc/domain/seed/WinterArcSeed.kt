package com.winterarc.domain.seed

import com.winterarc.domain.model.DayType
import com.winterarc.domain.model.Equipment
import com.winterarc.domain.model.Exercise
import com.winterarc.domain.model.MuscleGroup
import com.winterarc.domain.model.PlannedExercise
import com.winterarc.domain.model.Programme
import com.winterarc.domain.model.TrainingStyle
import com.winterarc.domain.model.WorkoutTemplate

/**
 * The starting content, derived from the source workbook.
 *
 * WHAT WAS CARRIED OVER: exercises, their order and A1/A2 pairings, sets, rep ranges, RIR,
 * rest periods, training style, primary muscle and the execution cues that change how a set
 * is performed.
 *
 * WHAT WAS DELIBERATELY LEFT OUT: the workbook's dashboards, volume audits, plan-review
 * commentary, version-comparison tables, change logs, nutrition, supplement and sleep
 * trackers. None of it changes what happens between two sets in a gym, and reproducing it
 * would have made the app a spreadsheet viewer instead of a training tool. The programme's
 * recovery and progression guidance survives as [PROGRESSION_NOTE] and per-exercise notes,
 * where it is actually read.
 *
 * WHICH PROGRAMME IS ACTIVE: the workbook contains a 4-day V1 and a 3-day V2, with a change
 * log (v2.1) documenting the move to V2. V2 is therefore seeded as the ACTIVE programme and
 * V1 is seeded alongside it, archived, so no prior work is lost and either can be run.
 *
 * Nothing here is hard-coded into a screen. This is first-run content that the user edits
 * freely afterwards; the app never reads these constants again once the database is seeded.
 */
object WinterArcSeed {

    const val PROGRESSION_NOTE: String =
        "Double progression: hit the top of the rep range on all sets at the target RIR, " +
            "then add load (+2.5 kg upper body, +5 kg lower). Reps drop to the bottom of the " +
            "range — that is expected — then rebuild over 2-4 weeks. If the same weight and " +
            "reps stall for 3 sessions, drop that lift 10% and rebuild rather than grinding it."

    // ---------------------------------------------------------------------
    // Exercise library
    // ---------------------------------------------------------------------

    private fun ex(
        id: String,
        name: String,
        muscle: MuscleGroup,
        equipment: Equipment,
        rest: Int,
        notes: String? = null,
    ) = Exercise(id, name, muscle, equipment, rest, notes)

    val exercises: List<Exercise> = listOf(
        // Push
        ex("ex-incline-press", "Incline Barbell / Smith Press", MuscleGroup.CHEST, Equipment.BARBELL, 180,
            "Upper-chest mass anchor. Heaviest press of the week."),
        ex("ex-flat-db-press", "Flat DB Press", MuscleGroup.CHEST, Equipment.DUMBBELL, 150,
            "Deep stretch at the bottom. Sternal thickness."),
        ex("ex-machine-chest-press", "Machine Chest Press", MuscleGroup.CHEST, Equipment.MACHINE, 75,
            "Second weekly chest stimulus."),
        ex("ex-cable-fly", "Cable / Machine Fly", MuscleGroup.CHEST, Equipment.CABLE, 75,
            "Stretch-position stimulus the presses miss."),
        ex("ex-db-shoulder-press", "Seated DB Shoulder Press", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, 120,
            "Dumbbells, not barbell — MMA striking already loads the shoulder."),
        ex("ex-cable-lateral-raise", "Cable Lateral Raise", MuscleGroup.SHOULDERS, Equipment.CABLE, 60,
            "Constant tension. Width is the cheapest visual win."),
        ex("ex-face-pull", "Face Pull", MuscleGroup.SHOULDERS, Equipment.CABLE, 45,
            "Rear delt and cuff. Shoulder insurance for striking volume."),
        // Pull
        ex("ex-pullup", "Pull-up (bodyweight)", MuscleGroup.BACK, Equipment.BODYWEIGHT, 180,
            "Width anchor. Progress reps first, then a 3s eccentric. Add load only at 4x12 clean."),
        ex("ex-chest-supported-row", "Chest-Supported / T-Bar Row", MuscleGroup.BACK, Equipment.MACHINE, 150,
            "Density driver. Chest-supported means no lower-back cost."),
        ex("ex-single-arm-db-row", "Single-Arm DB Row", MuscleGroup.BACK, Equipment.DUMBBELL, 90,
            "Full stretch at the bottom. Straps if grip is fatigued."),
        ex("ex-lat-pulldown", "Lat Pulldown (wide)", MuscleGroup.BACK, Equipment.CABLE, 90,
            "Second width dose at a different angle."),
        ex("ex-seated-cable-row", "Seated Cable Row", MuscleGroup.BACK, Equipment.CABLE, 75,
            "Back thickness, second weekly dose."),
        ex("ex-db-shrug", "DB Shrugs", MuscleGroup.TRAPS, Equipment.DUMBBELL, 60,
            "Placed early in the block on purpose — this is the set most often skipped."),
        // Arms — triceps
        ex("ex-close-grip-bench", "Close-Grip Bench Press", MuscleGroup.TRICEPS, Equipment.BARBELL, 150,
            "The arm-size lift. Triceps are two thirds of the arm."),
        ex("ex-overhead-tricep-ext", "Overhead Cable / DB Extension", MuscleGroup.TRICEPS, Equipment.CABLE, 90,
            "The long head only grows in the stretched position. Builds the horseshoe."),
        ex("ex-rope-pushdown", "Rope / V-Bar Pushdown", MuscleGroup.TRICEPS, Equipment.CABLE, 60,
            "Lateral head. Last set as a drop set or myo-reps."),
        // Arms — biceps
        ex("ex-standing-db-curl", "Standing DB Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL, 90,
            "Mass builder. Dumbbells allow active supination. No hip drive."),
        ex("ex-incline-db-curl", "Incline DB Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL, 90,
            "Peak builder. Arm behind the torso for a long-head stretch."),
        ex("ex-preacher-curl", "Preacher / Spider Curl", MuscleGroup.BICEPS, Equipment.BARBELL, 75,
            "Short head — arm width from the front. Slow negative."),
        ex("ex-hammer-curl", "Hammer Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL, 60,
            "Brachialis pushes the biceps and triceps apart, thickening the arm."),
        ex("ex-cable-curl", "Cable Curl", MuscleGroup.BICEPS, Equipment.CABLE, 45,
            "Pump dose. Metabolite stimulus is the point."),
        ex("ex-behind-body-cable-curl", "Behind-Body Cable Curl", MuscleGroup.BICEPS, Equipment.CABLE, 60,
            "Long head at its most lengthened. Peak emphasis."),
        // Legs
        ex("ex-back-squat", "Back Squat / Hack Squat", MuscleGroup.QUADS, Equipment.BARBELL, 180,
            "Leave 2-3 reps in reserve. Development, not a meet."),
        ex("ex-rdl", "Romanian Deadlift", MuscleGroup.HAMSTRINGS, Equipment.BARBELL, 150,
            "Controlled eccentric. Never grind these."),
        ex("ex-leg-press", "Leg Press / Bulgarian Split Squat", MuscleGroup.QUADS, Equipment.MACHINE, 120,
            "Single-leg option adds athletic carryover."),
        ex("ex-seated-leg-curl", "Seated Leg Curl", MuscleGroup.HAMSTRINGS, Equipment.MACHINE, 90,
            "Direct knee flexion — RDLs alone are not enough."),
        ex("ex-calf-raise", "Standing Calf Raise", MuscleGroup.CALVES, Equipment.MACHINE, 60,
            "Pause at full stretch."),
    )

    // ---------------------------------------------------------------------
    // V2 — the active 3-day programme
    // ---------------------------------------------------------------------

    private fun pe(
        id: String,
        exerciseId: String,
        position: Int,
        sets: Int,
        repLow: Int,
        repHigh: Int,
        rest: Int,
        style: TrainingStyle,
        rir: String,
        superset: String? = null,
        weight: Double? = null,
    ) = PlannedExercise(
        id = id,
        exerciseId = exerciseId,
        position = position,
        supersetGroup = superset,
        sets = sets,
        repLow = repLow,
        repHigh = repHigh,
        targetWeightKg = weight,
        rir = rir,
        restSeconds = rest,
        style = style,
    )

    /**
     * Starting loads are taken only from figures the workbook actually recorded — its
     * strength-target "now" column and its PR log. Where no figure was recorded the target is
     * left empty rather than invented; the app fills it from the previous session's real
     * performance the first time the movement is trained.
     */
    private val tuesday = WorkoutTemplate(
        id = "tpl-v2-tue",
        programmeId = "prog-v2",
        name = "Upper Push + Arms",
        dayOfWeek = 2,
        dayType = DayType.TRAINING,
        muscleGroups = listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS, MuscleGroup.BICEPS),
        position = 0,
        notes = "Follows Monday MMA. Front-delt volume is capped here on purpose.",
        exercises = listOf(
            pe("v2-tue-1", "ex-incline-press", 0, 4, 5, 8, 180, TrainingStyle.HEAVY_STRENGTH, "2", "A", 55.0),
            pe("v2-tue-2", "ex-flat-db-press", 1, 3, 8, 10, 150, TrainingStyle.HYPERTROPHY, "1-2", "A", 27.5),
            pe("v2-tue-3", "ex-db-shoulder-press", 2, 3, 6, 10, 120, TrainingStyle.HYPERTROPHY, "2", "B"),
            pe("v2-tue-4", "ex-close-grip-bench", 3, 3, 6, 8, 150, TrainingStyle.HEAVY_STRENGTH, "1-2", "B", 50.0),
            pe("v2-tue-5", "ex-overhead-tricep-ext", 4, 3, 10, 12, 90, TrainingStyle.LENGTHENED, "1", "C"),
            pe("v2-tue-6", "ex-cable-lateral-raise", 5, 3, 12, 20, 60, TrainingStyle.PUMP, "0-1", "C"),
            pe("v2-tue-7", "ex-incline-db-curl", 6, 3, 8, 10, 90, TrainingStyle.LENGTHENED, "1", null, 17.5),
        ),
    )

    private val thursday = WorkoutTemplate(
        id = "tpl-v2-thu",
        programmeId = "prog-v2",
        name = "Upper Pull + Arms",
        dayOfWeek = 4,
        dayType = DayType.TRAINING,
        muscleGroups = listOf(MuscleGroup.BACK, MuscleGroup.BICEPS, MuscleGroup.SHOULDERS),
        position = 1,
        notes = "Follows Wednesday MMA — grip arrives fatigued, so pull-ups go first and straps are fine on rows.",
        exercises = listOf(
            pe("v2-thu-1", "ex-pullup", 0, 4, 8, 12, 180, TrainingStyle.HEAVY_STRENGTH, "2", "A", 0.0),
            pe("v2-thu-2", "ex-chest-supported-row", 1, 4, 8, 10, 150, TrainingStyle.HYPERTROPHY, "1-2", "A", 40.0),
            pe("v2-thu-3", "ex-single-arm-db-row", 2, 3, 10, 12, 90, TrainingStyle.HYPERTROPHY, "1", "B"),
            pe("v2-thu-4", "ex-lat-pulldown", 3, 3, 10, 12, 90, TrainingStyle.HYPERTROPHY, "1", "B", 55.0),
            pe("v2-thu-5", "ex-standing-db-curl", 4, 3, 8, 10, 90, TrainingStyle.HYPERTROPHY, "1", "C", 17.5),
            pe("v2-thu-6", "ex-face-pull", 5, 3, 15, 20, 45, TrainingStyle.PUMP, "1", "C"),
            pe("v2-thu-7", "ex-preacher-curl", 6, 3, 8, 12, 75, TrainingStyle.SHORTENED, "1", "D", 20.0),
            pe("v2-thu-8", "ex-hammer-curl", 7, 3, 10, 12, 60, TrainingStyle.HYPERTROPHY, "1", "D"),
        ),
    )

    private val saturday = WorkoutTemplate(
        id = "tpl-v2-sat",
        programmeId = "prog-v2",
        name = "Legs + Upper Pump",
        dayOfWeek = 6,
        dayType = DayType.TRAINING,
        muscleGroups = listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.CHEST, MuscleGroup.BACK),
        position = 2,
        notes = "Follows Friday MMA and precedes the rest day. Squat at 2-3 RIR, never to failure.",
        exercises = listOf(
            pe("v2-sat-1", "ex-back-squat", 0, 4, 5, 8, 180, TrainingStyle.HEAVY_STRENGTH, "2-3", "A", 90.0),
            pe("v2-sat-2", "ex-rdl", 1, 3, 8, 10, 150, TrainingStyle.HYPERTROPHY, "2-3", "A", 80.0),
            pe("v2-sat-3", "ex-leg-press", 2, 3, 10, 12, 120, TrainingStyle.HYPERTROPHY, "1-2", "B"),
            pe("v2-sat-4", "ex-seated-leg-curl", 3, 3, 10, 12, 90, TrainingStyle.HYPERTROPHY, "1", "B"),
            pe("v2-sat-5", "ex-machine-chest-press", 4, 3, 12, 15, 75, TrainingStyle.PUMP, "1", "C", 60.0),
            pe("v2-sat-6", "ex-seated-cable-row", 5, 3, 12, 15, 75, TrainingStyle.PUMP, "1", "C"),
            pe("v2-sat-7", "ex-db-shrug", 6, 3, 10, 15, 60, TrainingStyle.HYPERTROPHY, "1", "D"),
            pe("v2-sat-8", "ex-calf-raise", 7, 3, 10, 15, 60, TrainingStyle.PUMP, "0-1", "D"),
            pe("v2-sat-9", "ex-rope-pushdown", 8, 3, 12, 15, 60, TrainingStyle.PUMP, "0-1", "E", 55.0),
            pe("v2-sat-10", "ex-behind-body-cable-curl", 9, 3, 10, 15, 60, TrainingStyle.LENGTHENED, "0-1", "E"),
        ),
    )

    private val monday = WorkoutTemplate(
        id = "tpl-v2-mon",
        programmeId = "prog-v2",
        name = "Pre-MMA Pull-ups",
        dayOfWeek = 1,
        dayType = DayType.TRAINING,
        muscleGroups = listOf(MuscleGroup.BACK),
        position = 3,
        notes = "20 minutes before MMA, never after. Never to failure. First thing to cut if " +
            "weight loss stalls, MMA quality drops, or any lift stalls twice.",
        exercises = listOf(
            pe("v2-mon-1", "ex-pullup", 0, 4, 6, 10, 150, TrainingStyle.MODERATE, "2-3", null, 0.0),
        ),
    )

    private val wednesday = WorkoutTemplate(
        id = "tpl-v2-wed", programmeId = "prog-v2", name = "MMA", dayOfWeek = 3,
        dayType = DayType.MMA, position = 4,
        notes = "No lifting. Wednesday sits 24h from both heavy days — there is no clean slot here.",
    )

    private val friday = WorkoutTemplate(
        id = "tpl-v2-fri", programmeId = "prog-v2", name = "MMA", dayOfWeek = 5,
        dayType = DayType.MMA, position = 5,
        notes = "No lifting. Saturday is legs; this slot stays empty.",
    )

    private val sunday = WorkoutTemplate(
        id = "tpl-v2-sun", programmeId = "prog-v2", name = "Rest", dayOfWeek = 7,
        dayType = DayType.REST, position = 6,
        notes = "A true rest day, not active recovery that becomes a session.",
    )

    val activeProgramme = Programme(
        id = "prog-v2",
        name = "Winter Arc V2 — 3 Lift Days",
        description = "Three lifting days around three MMA sessions, with Sunday as full rest. " +
            "Arms and back are the priority. " + PROGRESSION_NOTE,
        isActive = true,
        templates = listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday),
    )

    // ---------------------------------------------------------------------
    // V1 — kept, archived
    // ---------------------------------------------------------------------

    private val v1Tuesday = WorkoutTemplate(
        id = "tpl-v1-tue", programmeId = "prog-v1", name = "Heavy Push + Quads",
        dayOfWeek = 2, position = 0, isArchived = true,
        muscleGroups = listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.QUADS),
        exercises = listOf(
            pe("v1-tue-1", "ex-incline-press", 0, 4, 5, 8, 180, TrainingStyle.HEAVY_STRENGTH, "2", "A", 55.0),
            pe("v1-tue-2", "ex-flat-db-press", 1, 3, 8, 10, 150, TrainingStyle.HYPERTROPHY, "1-2", "A", 27.5),
            pe("v1-tue-3", "ex-cable-fly", 2, 3, 12, 15, 75, TrainingStyle.LENGTHENED, "1", "B"),
            pe("v1-tue-4", "ex-db-shoulder-press", 3, 3, 8, 10, 120, TrainingStyle.HYPERTROPHY, "2", "B"),
            pe("v1-tue-5", "ex-leg-press", 4, 3, 8, 12, 150, TrainingStyle.HYPERTROPHY, "2-3", "C"),
            pe("v1-tue-6", "ex-cable-lateral-raise", 5, 3, 15, 20, 60, TrainingStyle.PUMP, "0-1", "C"),
            pe("v1-tue-7", "ex-calf-raise", 6, 3, 10, 15, 60, TrainingStyle.PUMP, "0-1", "C"),
        ),
    )

    private val v1Thursday = WorkoutTemplate(
        id = "tpl-v1-thu", programmeId = "prog-v1", name = "Heavy Pull + Posterior",
        dayOfWeek = 4, position = 1, isArchived = true,
        muscleGroups = listOf(MuscleGroup.BACK, MuscleGroup.HAMSTRINGS),
        exercises = listOf(
            pe("v1-thu-1", "ex-pullup", 0, 4, 8, 12, 150, TrainingStyle.HEAVY_STRENGTH, "2", "A", 0.0),
            pe("v1-thu-2", "ex-chest-supported-row", 1, 4, 8, 10, 150, TrainingStyle.HYPERTROPHY, "1-2", "A", 40.0),
            pe("v1-thu-3", "ex-single-arm-db-row", 2, 3, 10, 12, 90, TrainingStyle.HYPERTROPHY, "1", "B"),
            pe("v1-thu-4", "ex-face-pull", 3, 3, 15, 20, 45, TrainingStyle.PUMP, "1", "B"),
            pe("v1-thu-5", "ex-rdl", 4, 3, 8, 10, 150, TrainingStyle.HYPERTROPHY, "2-3", null, 80.0),
        ),
    )

    private val v1Saturday = WorkoutTemplate(
        id = "tpl-v1-sat", programmeId = "prog-v1", name = "Dedicated Arm Day",
        dayOfWeek = 6, position = 2, isArchived = true,
        muscleGroups = listOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS),
        exercises = listOf(
            pe("v1-sat-1", "ex-close-grip-bench", 0, 4, 6, 8, 150, TrainingStyle.HEAVY_STRENGTH, "1-2", "A", 50.0),
            pe("v1-sat-2", "ex-overhead-tricep-ext", 1, 3, 10, 12, 90, TrainingStyle.LENGTHENED, "1", "A"),
            pe("v1-sat-3", "ex-rope-pushdown", 2, 3, 12, 15, 60, TrainingStyle.PUMP, "0-1", "A", 55.0),
            pe("v1-sat-4", "ex-standing-db-curl", 3, 4, 8, 10, 90, TrainingStyle.HYPERTROPHY, "1", "B", 17.5),
            pe("v1-sat-5", "ex-incline-db-curl", 4, 3, 8, 12, 90, TrainingStyle.LENGTHENED, "1", "B", 17.5),
            pe("v1-sat-6", "ex-preacher-curl", 5, 3, 10, 12, 75, TrainingStyle.SHORTENED, "1", "B", 20.0),
        ),
    )

    val archivedProgramme = Programme(
        id = "prog-v1",
        name = "Winter Arc V1 — 4 Day (archived)",
        description = "The original four-day split, kept for reference. Superseded by V2, which " +
            "added a true rest day and rebalanced arm frequency.",
        isActive = false,
        templates = listOf(v1Tuesday, v1Thursday, v1Saturday),
    )

    val programmes: List<Programme> = listOf(activeProgramme, archivedProgramme)

    // ---------------------------------------------------------------------
    // Baseline body measurements
    // ---------------------------------------------------------------------

    /**
     * The user's stated current figures, stored canonically (kg / cm).
     * Treated strictly as a starting point, not as history: they are entered as a single
     * check-in dated on first run and are fully editable afterwards.
     */
    object Baseline {
        const val WEIGHT_KG = 80.5
        const val HEIGHT_CM = 170.18   // 5 feet 7 inches
        const val CHEST_CM = 106.68    // 42 in
        const val WAIST_CM = 96.52     // 38 in
        const val BICEPS_CM = 40.64    // 16 in
    }
}
