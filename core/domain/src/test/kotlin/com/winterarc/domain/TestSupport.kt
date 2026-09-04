package com.winterarc.domain

import com.winterarc.domain.model.*
import com.winterarc.domain.programme.Clock
import com.winterarc.domain.programme.IdGenerator
import com.winterarc.domain.programme.WorkoutEngine
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class FakeClock(
    var instant: Instant = Instant.parse("2026-09-04T09:00:00Z"),
    var date: LocalDate = LocalDate.of(2026, 9, 4),
) : Clock {
    override fun now(): Instant = instant
    override fun today(): LocalDate = date
    fun advanceSeconds(s: Long) { instant = instant.plusSeconds(s) }
}

class SeqIds(prefix: String = "id") : IdGenerator {
    private val n = AtomicInteger(0)
    private val p = prefix
    override fun next(): String = "$p-${n.incrementAndGet()}"
}

fun testEngine(clock: FakeClock = FakeClock()) = WorkoutEngine(clock, SeqIds())

fun planned(
    id: String,
    exerciseId: String,
    position: Int,
    sets: Int = 3,
    repLow: Int = 8,
    repHigh: Int = 12,
    weight: Double? = null,
    rest: Int = 90,
    superset: String? = null,
) = PlannedExercise(
    id = id,
    exerciseId = exerciseId,
    position = position,
    supersetGroup = superset,
    sets = sets,
    repLow = repLow,
    repHigh = repHigh,
    targetWeightKg = weight,
    restSeconds = rest,
)

fun template(vararg exercises: PlannedExercise) = WorkoutTemplate(
    id = "tpl-1",
    programmeId = "prog-1",
    name = "Test Day",
    dayOfWeek = 2,
    exercises = exercises.toList(),
)

/** Builds a completed session directly, for history fixtures in PR tests. */
fun completedSession(
    id: String,
    date: LocalDate,
    exerciseId: String,
    sets: List<Pair<Double, Int>>,
    plannedSets: Int = 3,
): WorkoutSession {
    val peId = "$id-pe"
    return WorkoutSession(
        id = id,
        templateId = "tpl-1",
        programmeId = "prog-1",
        name = "Session $id",
        date = date,
        startedAt = Instant.parse("2026-01-01T09:00:00Z"),
        finishedAt = Instant.parse("2026-01-01T10:00:00Z"),
        status = SessionStatus.COMPLETED,
        exercises = listOf(
            PerformedExercise(
                id = peId,
                sessionId = id,
                exerciseId = exerciseId,
                position = 0,
                plannedSets = plannedSets,
                plannedRepLow = 8,
                plannedRepHigh = 12,
                plannedRestSeconds = 90,
                sets = sets.mapIndexed { i, (w, r) ->
                    ActualSet(
                        id = "$peId-s${i + 1}",
                        performedExerciseId = peId,
                        setNumber = i + 1,
                        weightKg = w,
                        reps = r,
                    )
                },
            ),
        ),
    )
}
