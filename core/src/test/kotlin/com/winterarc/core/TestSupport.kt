package com.winterarc.core

import java.time.LocalDate

/** Fixed reference date so nothing in the suite depends on when it is run. */
val TODAY: LocalDate = LocalDate.of(2026, 9, 5) // a Saturday

fun set(weight: Double, reps: Int, done: Boolean = true, warmup: Boolean = false) =
    SetEntry(weightKg = weight, reps = reps, done = done, warmup = warmup)

fun sessionExercise(
    exerciseId: String,
    sets: List<SetEntry>,
    plannedSets: Int = sets.size,
    group: String = "A1",
) = SessionExercise(
    exerciseId = exerciseId,
    group = group,
    plannedSets = plannedSets,
    repLow = 8,
    repHigh = 12,
    sets = sets,
)

fun finishedSession(
    date: LocalDate,
    exercises: List<SessionExercise>,
    id: String = newId(),
    title: String = "Session",
) = Session(
    id = id,
    date = date,
    startedAtMillis = 1_000L,
    finishedAtMillis = 1_000L + 60 * 60 * 1000L,
    title = title,
    exercises = exercises,
)

/**
 * A seeded install with optional history. Passing no body list keeps the seeded starting
 * check-in, because that is what a real first run has -- wiping it would let tests pass against
 * a state the app never actually reaches.
 */
fun baseData(sessions: List<Session> = emptyList(), body: List<BodyEntry>? = null): AppData {
    val seed = Seed.initial(TODAY)
    return seed.copy(sessions = sessions, body = body ?: seed.body)
}
