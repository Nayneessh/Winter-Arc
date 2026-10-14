package com.winterarc.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * WINTER ARC v4.1 — starts Monday 19 October.
 *
 * Transcribed from the planning sheet exactly: order, supersets, sets, rep ranges, RIR, rest,
 * style, start load and the progression cue on every movement.
 *
 * Two conventions from the sheet are encoded rather than written out. A1/A2 means a superset, so
 * the first movement of a pair carries no rest -- you go straight into the second -- and the pair's
 * rest sits on the second. And the start load becomes the plan's target weight, which the app uses
 * to pre-fill a movement that has never been performed before; once there is history, the last
 * performance wins and this is ignored.
 */
object Programme41 {

    const val ID = "programme-winter-arc-v4-1"

    // -- movements this plan introduces --------------------------------------------------------
    //
    // Only genuinely new ones. Anything the old plan already had is reused by id so that its
    // history, records and "last time" prefill carry straight over.

    val newExercises: List<Exercise> = listOf(
        Exercise(
            "incline-db-press", "Incline DB Press (30°)", Muscle.CHEST,
            "Upper chest", Equipment.DUMBBELL,
        ),
        Exercise(
            "low-high-cable-fly", "Low-to-High Cable Fly", Muscle.CHEST,
            "Upper chest, lengthened", Equipment.CABLE,
        ),
        Exercise(
            "underhand-lat-pulldown", "Underhand Lat Pulldown", Muscle.BACK,
            "Lower lats — width", Equipment.CABLE,
        ),
        Exercise(
            "reverse-pec-deck", "Reverse Pec Deck", Muscle.SHOULDERS,
            "Rear delt", Equipment.MACHINE,
        ),
        Exercise(
            "ez-bar-curl", "EZ-Bar Curl", Muscle.BICEPS,
            "Biceps — mass", Equipment.BARBELL,
        ),
        Exercise(
            "wide-grip-pushdown", "Wide-Grip Cable Pushdown", Muscle.TRICEPS,
            "Triceps lateral / medial", Equipment.CABLE,
        ),
    )

    private fun item(
        exerciseId: String,
        group: String,
        sets: Int,
        repLow: Int,
        repHigh: Int,
        rir: String,
        rest: Int,
        style: String,
        load: Double?,
        cue: String,
        priority: Priority = Priority.NONE,
    ) = PlanItem(
        id = "v41-$group-$exerciseId",
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
        targetWeightKg = load,
    )

    // -- MONDAY — 9 sets -----------------------------------------------------------------------

    private val monday = Routine(
        id = "v41-mon",
        name = "MMA + Legs & Back",
        subtitle = "Post-MMA 25 min · legs + back thickness",
        accent = Accent.GREEN,
        days = listOf(DayOfWeek.MONDAY),
        estimatedMinutes = 25,
        note = "After MMA, never before. 9 sets — keep it light enough that Thursday is unaffected.",
        items = listOf(
            item("leg-press", "A1", 3, 10, 12, "2-3", 0, "Moderate", 75.0,
                "No spinal load after MMA. +5 kg when 3×12 at RIR 2.", Priority.LEGS),
            item("single-arm-row", "A2", 3, 10, 12, "2", 90, "Hypertrophy", 30.0,
                "Back dose #1 — 72 h before Thursday. Straps allowed.", Priority.BACK),
            item("calf-raise", "B1", 3, 12, 15, "1", 60, "Hypertrophy", null,
                "Find your 15RM. Two-second pause at full stretch."),
        ),
    )

    // -- TUESDAY — 19 sets ---------------------------------------------------------------------

    private val tuesday = Routine(
        id = "v41-tue",
        name = "Chest + Delts + Triceps",
        subtitle = "Chest dose #1 · ~60 min",
        accent = Accent.BLUE,
        days = listOf(DayOfWeek.TUESDAY),
        estimatedMinutes = 60,
        items = listOf(
            item("incline-db-press", "A1", 4, 6, 10, "2", 165, "Heavy", 25.0,
                "Main chest lift. +2.5 kg/hand when you hit 4×10."),
            item("low-high-cable-fly", "B1", 3, 10, 12, "1-2", 90, "Lengthened", null,
                "Find your 12RM. Hands travel from below the hips to eye level."),
            item("pec-deck", "B2", 3, 10, 12, "1-2", 90, "Lengthened", 60.0,
                "One-second pause in the stretch. No drop sets."),
            item("cable-lateral-raise", "C1", 3, 12, 15, "1", 0, "Hypertrophy", 7.5,
                "Superset with C2."),
            item("face-pull", "C2", 3, 15, 15, "2", 75, "Hypertrophy", 25.0,
                "Shoulder insurance for MMA."),
            item("pushdown", "D1", 3, 10, 12, "1-2", 75, "Hypertrophy", 50.0,
                "Triceps dose #1 — four days before arm day.", Priority.ARMS),
        ),
    )

    // -- WEDNESDAY — 8 sets --------------------------------------------------------------------

    private val wednesday = Routine(
        id = "v41-wed",
        name = "MMA + Preacher & Hamstrings",
        subtitle = "Post-MMA 25 min · preacher + hamstrings + core",
        accent = Accent.GOLD,
        days = listOf(DayOfWeek.WEDNESDAY),
        estimatedMinutes = 25,
        note = "After MMA, never before.",
        items = listOf(
            item("preacher-curl", "A1", 3, 8, 12, "1-2", 0, "Shortened", null,
                "Find your 10RM. Isolated here, three days before arm day.", Priority.ARMS),
            item("leg-curl", "A2", 3, 10, 12, "2", 90, "Hypertrophy", null,
                "Find your 12RM. Knee flexion.", Priority.LEGS),
            item("hanging-leg-raise", "B1", 2, 10, 15, "2", 60, "", null,
                "Bodyweight, or hold 7.5 kg. Core for MMA."),
        ),
    )

    // -- THURSDAY — 20 sets --------------------------------------------------------------------

    private val thursday = Routine(
        id = "v41-thu",
        name = "Back + Shoulder Press",
        subtitle = "Back · Delts · ~65 min",
        accent = Accent.GREEN,
        days = listOf(DayOfWeek.THURSDAY),
        estimatedMinutes = 65,
        items = listOf(
            item("pull-up", "A1", 3, 6, 12, "2", 165, "Heavy", null,
                "Bodyweight — log 5×8. Progress reps to 3×12, then a 3 s eccentric. " +
                    "No plates while cutting.", Priority.BACK),
            item("t-bar-row", "A2", 4, 6, 10, "2", 150, "Heavy", 45.0,
                "Thickness lift, zero lower-back cost. Squeeze one second at the ribs.",
                Priority.BACK),
            item("underhand-lat-pulldown", "B1", 3, 8, 12, "1-2", 120, "Hypertrophy", 55.0,
                "Shoulder-width underhand grip, elbows driven to the hips.", Priority.BACK),
            item("db-shoulder-press", "C1", 3, 6, 10, "2", 150, "Heavy", 20.0,
                "Off chest day on purpose — 48 h after Tuesday."),
            item("db-lateral-raise", "D1", 3, 12, 15, "1", 0, "Hypertrophy", 10.0,
                "Superset with D2."),
            item("reverse-pec-deck", "D2", 2, 12, 15, "1-2", 60, "Hypertrophy", 30.0,
                "Rear delt, paired with the lateral raise."),
            item("shrug", "E1", 2, 10, 12, "2", 60, "Hypertrophy", 35.0,
                "Last movement — two sets only.", Priority.BACK),
        ),
    )

    // -- FRIDAY — 9 sets -----------------------------------------------------------------------

    private val friday = Routine(
        id = "v41-fri",
        name = "MMA + Hinge & Chest",
        subtitle = "Post-MMA 25 min · hinge + chest #2 + quads",
        accent = Accent.BLUE,
        days = listOf(DayOfWeek.FRIDAY),
        estimatedMinutes = 25,
        note = "After MMA, never before.",
        items = listOf(
            item("romanian-deadlift", "A1", 3, 8, 10, "2-3", 0, "Moderate", 60.0,
                "Straps — save the grip for Saturday. 72 h and a Sunday before Monday's MMA.",
                Priority.LEGS),
            item("machine-chest-press", "A2", 3, 10, 12, "2", 120, "Hypertrophy", 65.0,
                "Chest back to twice a week. Three days after Tuesday."),
            item("leg-extension", "B1", 3, 12, 15, "1-2", 60, "Hypertrophy", 55.0,
                "Low-fatigue quad work.", Priority.LEGS),
        ),
    )

    // -- SATURDAY — 19 sets --------------------------------------------------------------------

    private val saturday = Routine(
        id = "v41-sat",
        name = "Arm Day",
        subtitle = "Triceps · Biceps — the priority session",
        accent = Accent.GOLD,
        days = listOf(DayOfWeek.SATURDAY),
        estimatedMinutes = 60,
        note = "Antagonist pairs throughout — neither muscle tires the other.",
        items = listOf(
            item("close-grip-bench", "A1", 3, 6, 8, "2", 0, "Heavy", 50.0,
                "Log 60×7. +2.5 kg when you hit 3×8 at RIR 2.", Priority.ARMS),
            item("ez-bar-curl", "A2", 3, 6, 10, "2", 150, "Heavy", 25.0,
                "Find your 8RM, around 25-30 kg. Antagonist pair with the close-grip bench.",
                Priority.ARMS),
            item("overhead-extension", "B1", 3, 10, 12, "1-2", 0, "Lengthened", 25.0,
                "The horseshoe lift. Log 30×10.", Priority.ARMS),
            item("incline-db-curl", "B2", 3, 8, 12, "1-2", 120, "Lengthened", 10.0,
                "Both arms. Arms behind the torso, supinate at the top.", Priority.ARMS),
            item("wide-grip-pushdown", "C1", 3, 10, 12, "1", 0, "Hypertrophy", 50.0,
                "No drop set.", Priority.ARMS),
            item("standing-db-curl", "C2", 2, 8, 12, "1", 90, "Hypertrophy", 15.0,
                "Two sets only — it overlaps with the EZ curl.", Priority.ARMS),
            item("hammer-curl", "D1", 2, 10, 12, "1", 75, "Hypertrophy", 12.5,
                "Arm width, and grip for MMA.", Priority.ARMS),
        ),
    )

    val programme = Programme(
        id = ID,
        name = "Winter Arc v4.1",
        startDate = LocalDate.of(2026, 10, 19),
        endDate = LocalDate.of(2026, 12, 31),
        routines = listOf(monday, tuesday, wednesday, thursday, friday, saturday),
        active = true,
    )

    /** The rules the sheet states, shown on the programme screen. */
    val principles: List<Pair<String, String>> = listOf(
        "Even spread" to
            "84 sets a week, deliberately distributed. Heavy days stay at or under 20 sets, " +
            "MMA days at or under 9. The point is no junk fatigue.",
        "Nothing to failure" to
            "Every movement carries an RIR. Leaving reps in the tank is what lets six days a " +
            "week accumulate instead of grind.",
        "Supersets" to
            "A1/A2 alternate, and the rest comes after the pair rather than between the two.",
        "Light days" to
            "Monday, Wednesday and Friday work is done AFTER MMA. Never before.",
        "Sunday" to
            "Complete rest. Prescribed, not earned — which is why streaks here count weeks, " +
            "not days.",
    )
}
