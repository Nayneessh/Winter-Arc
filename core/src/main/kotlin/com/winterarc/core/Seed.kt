package com.winterarc.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * First-run content: the exercise catalogue and the Winter Arc programme.
 *
 * The programme is transcribed from the source planning workbook -- order, supersets, sets, rep
 * ranges, RIR, rest, style and the execution cue attached to each movement. Everything seeded
 * here is ordinary editable data. After the first launch the seed is never consulted again, so
 * changing a rep range or deleting a movement sticks.
 */
object Seed {

    // -- catalogue -------------------------------------------------------------------------------
    //
    // Ids are readable slugs rather than random values so that a seeded goal can reference a
    // seeded movement, and so a hand-inspected export is intelligible.

    private fun ex(
        id: String,
        name: String,
        muscle: Muscle,
        detail: String = "",
        equipment: Equipment = Equipment.OTHER,
        bodyweight: Boolean = false,
    ) = Exercise(id, name, muscle, detail, equipment, bodyweight)

    val catalogue: List<Exercise> = listOf(
        // Chest
        ex("incline-press", "Incline Barbell / Smith Press", Muscle.CHEST, "Upper chest", Equipment.BARBELL),
        ex("flat-db-press", "Flat DB Press", Muscle.CHEST, "Mid chest", Equipment.DUMBBELL),
        ex("cable-fly", "Cable / Machine Fly", Muscle.CHEST, "Chest, lengthened", Equipment.CABLE),
        ex("machine-chest-press", "Machine Chest Press", Muscle.CHEST, "Chest", Equipment.MACHINE),
        ex("bench-press", "Barbell Bench Press", Muscle.CHEST, "Mid chest", Equipment.BARBELL),
        ex("dip", "Dip", Muscle.CHEST, "Lower chest / triceps", Equipment.BODYWEIGHT, bodyweight = true),
        ex("pec-deck", "Pec Deck", Muscle.CHEST, "Chest, shortened", Equipment.MACHINE),

        // Back
        ex("pull-up", "Pull-up", Muscle.BACK, "Lats — width", Equipment.BODYWEIGHT, bodyweight = true),
        ex("chin-up", "Chin-up", Muscle.BACK, "Lats / biceps", Equipment.BODYWEIGHT, bodyweight = true),
        ex("t-bar-row", "Chest-Supported / T-Bar Row", Muscle.BACK, "Mid-back — thickness", Equipment.MACHINE),
        ex("single-arm-row", "Single-Arm DB Row", Muscle.BACK, "Lats — thickness", Equipment.DUMBBELL),
        ex("lat-pulldown", "Lat Pulldown", Muscle.BACK, "Back width", Equipment.CABLE),
        ex("seated-row", "Seated Cable Row", Muscle.BACK, "Mid-back", Equipment.CABLE),
        ex("barbell-row", "Barbell Row", Muscle.BACK, "Mid-back", Equipment.BARBELL),
        ex("straight-arm-pulldown", "Straight-Arm Pulldown", Muscle.BACK, "Lats, lengthened", Equipment.CABLE),
        ex("deadlift", "Deadlift", Muscle.BACK, "Posterior chain", Equipment.BARBELL),
        ex("shrug", "Shrug", Muscle.BACK, "Traps", Equipment.DUMBBELL),

        // Shoulders
        ex("db-shoulder-press", "Seated DB Shoulder Press", Muscle.SHOULDERS, "Delts — anterior / lateral", Equipment.DUMBBELL),
        ex("cable-lateral-raise", "Cable Lateral Raise", Muscle.SHOULDERS, "Lateral delt", Equipment.CABLE),
        ex("db-lateral-raise", "DB Lateral Raise", Muscle.SHOULDERS, "Lateral delt", Equipment.DUMBBELL),
        ex("face-pull", "Face Pull", Muscle.SHOULDERS, "Rear delt / rotator cuff", Equipment.CABLE),
        ex("rear-delt-fly", "Rear Delt Fly", Muscle.SHOULDERS, "Rear delt", Equipment.DUMBBELL),
        ex("overhead-press", "Overhead Press", Muscle.SHOULDERS, "Anterior delt", Equipment.BARBELL),
        ex("arnold-press", "Arnold Press", Muscle.SHOULDERS, "Delts", Equipment.DUMBBELL),

        // Triceps
        ex("close-grip-bench", "Close-Grip Bench Press", Muscle.TRICEPS, "Triceps — mass", Equipment.BARBELL),
        ex("overhead-extension", "Overhead Cable / DB Extension", Muscle.TRICEPS, "Triceps — long head", Equipment.CABLE),
        ex("pushdown", "Rope / V-Bar Pushdown", Muscle.TRICEPS, "Triceps — lateral head", Equipment.CABLE),
        ex("skull-crusher", "Skull Crusher", Muscle.TRICEPS, "Triceps — long head", Equipment.BARBELL),

        // Biceps
        ex("standing-db-curl", "Standing DB Curl", Muscle.BICEPS, "Biceps — mass", Equipment.DUMBBELL),
        ex("incline-db-curl", "Incline DB Curl", Muscle.BICEPS, "Biceps — long head / peak", Equipment.DUMBBELL),
        ex("preacher-curl", "Preacher / Spider Curl", Muscle.BICEPS, "Biceps — short head", Equipment.DUMBBELL),
        ex("cable-curl", "Cable Curl", Muscle.BICEPS, "Biceps", Equipment.CABLE),
        ex("hammer-curl", "Hammer Curl", Muscle.BICEPS, "Brachialis / forearm", Equipment.DUMBBELL),
        ex("reverse-curl", "Reverse Curl", Muscle.BICEPS, "Brachioradialis", Equipment.BARBELL),

        // Legs
        ex("leg-press", "Leg Press / Hack Squat", Muscle.QUADS, "Quads / glutes", Equipment.MACHINE),
        ex("back-squat", "Back Squat", Muscle.QUADS, "Quads / glutes", Equipment.BARBELL),
        ex("leg-extension", "Leg Extension", Muscle.QUADS, "Quads", Equipment.MACHINE),
        ex("bulgarian-split-squat", "Bulgarian Split Squat", Muscle.QUADS, "Quads / glutes", Equipment.DUMBBELL),
        ex("romanian-deadlift", "Romanian Deadlift", Muscle.HAMSTRINGS, "Hamstrings / glutes", Equipment.BARBELL),
        ex("leg-curl", "Leg Curl", Muscle.HAMSTRINGS, "Hamstrings", Equipment.MACHINE),
        ex("hip-thrust", "Hip Thrust", Muscle.GLUTES, "Glutes", Equipment.BARBELL),
        ex("calf-raise", "Standing Calf Raise", Muscle.CALVES, "Calves", Equipment.MACHINE),

        // Core
        ex("hanging-leg-raise", "Hanging Leg Raise", Muscle.CORE, "Lower abs", Equipment.BODYWEIGHT, bodyweight = true),
        ex("cable-crunch", "Cable Crunch", Muscle.CORE, "Abs", Equipment.CABLE),
        ex("plank", "Plank", Muscle.CORE, "Bracing", Equipment.BODYWEIGHT, bodyweight = true),

        // Conditioning
        ex("mma", "MMA Training", Muscle.CONDITIONING, "Striking / grappling", Equipment.OTHER, bodyweight = true),
        ex("skipping", "Skipping", Muscle.CONDITIONING, "Conditioning", Equipment.OTHER, bodyweight = true),
        ex("run", "Run", Muscle.CONDITIONING, "Aerobic", Equipment.OTHER, bodyweight = true),
    )

    // -- programme -------------------------------------------------------------------------------

    private fun item(
        exerciseId: String,
        group: String,
        sets: Int,
        repLow: Int,
        repHigh: Int,
        rir: String,
        rest: Int,
        style: String,
        cue: String,
        priority: Priority = Priority.NONE,
    ) = PlanItem(
        exerciseId = exerciseId,
        group = group,
        sets = sets,
        repLow = repLow,
        repHigh = repHigh,
        rir = rir,
        restSeconds = rest,
        style = style,
        cue = cue,
        priority = priority,
    )

    private val tuesday = Routine(
        id = "routine-push",
        name = "Heavy Push + Quads",
        subtitle = "Chest · Delts · Quads",
        accent = Accent.BLUE,
        days = listOf(DayOfWeek.TUESDAY),
        estimatedMinutes = 90,
        items = listOf(
            item("incline-press", "A1", 4, 5, 8, "2", 180, "Heavy",
                "Upper-chest mass anchor. Heaviest press of the week."),
            item("flat-db-press", "A2", 3, 8, 10, "1-2", 150, "Hypertrophy",
                "Deep stretch at the bottom. 30 kg/hand is your current max."),
            item("cable-fly", "B1", 3, 12, 15, "1", 75, "Lengthened",
                "Stretch-position stimulus the presses miss. Superset with B2."),
            item("db-shoulder-press", "B2", 3, 8, 10, "2", 120, "Hypertrophy",
                "Dumbbells, not barbell — Monday's MMA striking already loaded the shoulder."),
            item("leg-press", "C1", 3, 8, 12, "2-3", 150, "Hypertrophy",
                "Machine-based on purpose: no spinal load 24 h before Wednesday MMA."),
            item("cable-lateral-raise", "C2", 3, 15, 20, "0-1", 60, "Pump",
                "Superset with C1. Width is your cheapest visual win."),
        ),
    )

    private val thursday = Routine(
        id = "routine-pull",
        name = "Heavy Pull + Posterior",
        subtitle = "Back · Rear delts · Hamstrings",
        accent = Accent.GREEN,
        days = listOf(DayOfWeek.THURSDAY),
        estimatedMinutes = 90,
        items = listOf(
            item("pull-up", "A1", 4, 8, 12, "2", 150, "Heavy-ish",
                "Progress reps, then a 3 s eccentric. Plates only once 4x12 is clean and weight is down.",
                Priority.BACK),
            item("t-bar-row", "A2", 4, 8, 10, "1-2", 150, "Heavy hypertrophy",
                "Density driver. Chest-supported means zero lower-back cost before Friday MMA.",
                Priority.BACK),
            item("single-arm-row", "B1", 3, 10, 12, "1", 90, "Hypertrophy",
                "Full stretch at the bottom. Straps if grip is MMA-fatigued.", Priority.BACK),
            item("face-pull", "B2", 3, 15, 20, "1", 45, "Pump",
                "Superset with B1. Shoulder insurance."),
            item("romanian-deadlift", "C1", 3, 8, 10, "2-3", 150, "Hypertrophy",
                "MODERATE load only — Friday MMA is 24 h away. Never grind these."),
        ),
    )

    private val saturday = Routine(
        id = "routine-arms",
        name = "Arm Day",
        subtitle = "Triceps · Biceps — the priority session",
        accent = Accent.GOLD,
        days = listOf(DayOfWeek.SATURDAY),
        estimatedMinutes = 88,
        note = "Trimmed to be finishable. Every movement here is priority work.",
        items = listOf(
            item("close-grip-bench", "A1", 4, 6, 8, "1-2", 150, "Heavy",
                "THE arm-size lift. Triceps are two thirds of the arm. Currently 30 kg — climb it.",
                Priority.ARMS),
            item("overhead-extension", "A2", 3, 10, 12, "1", 90, "Lengthened",
                "The long head only grows stretched. This builds the horseshoe.", Priority.ARMS),
            item("pushdown", "A3", 3, 12, 15, "0-1", 60, "Pump / drop",
                "Last set is a drop set. This is your metabolite work.", Priority.ARMS),
            item("standing-db-curl", "B1", 4, 8, 10, "1", 90, "Heavy hypertrophy",
                "Hold 17.5 kg until 10 reps across 4 sets, then move to 20 kg. No hip drive.",
                Priority.ARMS),
            item("incline-db-curl", "B2", 3, 8, 12, "1", 90, "Lengthened",
                "PEAK BUILDER. Arm behind the torso — the closest thing to a peak lift.",
                Priority.ARMS),
            item("preacher-curl", "B3", 3, 10, 12, "1", 75, "Shortened",
                "Front-on arm width. Slow negative.", Priority.ARMS),
        ),
    )

    private val monday = Routine(
        id = "routine-mon-light",
        name = "MMA + Back & Rear Delt",
        subtitle = "30 min light",
        accent = Accent.GREEN,
        days = listOf(DayOfWeek.MONDAY),
        estimatedMinutes = 30,
        note = "Do this AFTER MMA, never before.",
        items = listOf(
            item("lat-pulldown", "A1", 3, 10, 15, "2", 45, "Moderate",
                "10-15 at RIR 2 (~55-65 kg). 18-20 reps fails on grip, not lats — and grip is MMA-fatigued.",
                Priority.BACK),
            item("face-pull", "A2", 3, 15, 20, "0-1", 45, "Pump",
                "Superset with A1. Rear delt and cuff health."),
        ),
    )

    private val wednesday = Routine(
        id = "routine-wed-light",
        name = "MMA + Arms",
        subtitle = "30 min light",
        accent = Accent.GOLD,
        days = listOf(DayOfWeek.WEDNESDAY),
        estimatedMinutes = 30,
        note = "Do this AFTER MMA, never before. Three days clear of Saturday.",
        items = listOf(
            item("cable-curl", "A1", 3, 12, 15, "1", 45, "Pump",
                "Isolation work — the metabolite stimulus IS the point here.", Priority.ARMS),
            item("pushdown", "A2", 3, 12, 15, "0-1", 45, "Pump / drop",
                "Superset with A1. Last set is a drop set.", Priority.ARMS),
        ),
    )

    private val friday = Routine(
        id = "routine-fri-light",
        name = "MMA + Chest & Delts",
        subtitle = "30 min light",
        accent = Accent.BLUE,
        days = listOf(DayOfWeek.FRIDAY),
        estimatedMinutes = 30,
        note = "Do this AFTER MMA, never before.",
        items = listOf(
            item("machine-chest-press", "A1", 3, 10, 15, "2", 45, "Moderate",
                "10-15 at RIR 2 (~60-65 kg). 18-20 reps is local endurance — high joint cycles for little tension."),
            item("cable-lateral-raise", "A2", 3, 15, 20, "0-1", 45, "Pump",
                "Superset with A1. Stays 15-20, taken to failure."),
        ),
    )

    val programme = Programme(
        id = "programme-winter-arc",
        name = "Winter Arc",
        startDate = LocalDate.of(2026, 9, 7),
        endDate = LocalDate.of(2026, 12, 31),
        routines = listOf(monday, tuesday, wednesday, thursday, friday, saturday),
        active = true,
    )

    /** The rules from the workbook that govern how the programme is run. */
    val principles: List<Pair<String, String>> = listOf(
        "Recomp rule" to
            "Eat at maintenance, hit protein, let fat fall while the lifts rise. The scale should barely move.",
        "Deload" to
            "Every sixth week. Non-negotiable — there are zero rest days across seven weekly sessions.",
        "Stalling" to
            "If a lift stalls for two sessions, or elbow pain appears, fix that before adding load.",
        "Light days" to
            "Monday, Wednesday and Friday work is done AFTER MMA. Never before.",
        "Sunday" to
            "Rest. It is prescribed, not earned — which is why streaks here count weeks, not days.",
    )

    private val goals = Goals(
        startWeightKg = 80.0,
        targetWeightKg = 73.0,
        startBodyFatPct = 24.0,
        targetBodyFatPct = 15.0,
        weeklySessionTarget = 6,
        liftGoals = listOf(
            LiftGoal(
                id = "goal-db-curl",
                exerciseId = "standing-db-curl",
                label = "DB Curl / hand",
                startKg = 17.5,
                milestoneKg = 21.0,
                targetKg = 25.0,
            ),
        ),
    )

    /**
     * The starting state for a fresh install.
     *
     * One body entry is seeded from the workbook's own figures so the goal rings have something
     * to measure from on day one. It is an ordinary entry and can be edited or deleted.
     */
    fun initial(today: LocalDate): AppData = AppData(
        exercises = catalogue,
        programmes = listOf(programme),
        sessions = emptyList(),
        body = listOf(
            BodyEntry(
                id = "body-seed",
                date = today,
                weightKg = 80.0,
                bodyFatPct = 24.0,
                note = "Starting point, from the plan.",
            ),
        ),
        goals = goals,
        prefs = Prefs(),
    )
}
