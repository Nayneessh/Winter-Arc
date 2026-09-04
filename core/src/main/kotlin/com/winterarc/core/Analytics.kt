package com.winterarc.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Result types
// ---------------------------------------------------------------------------------------------

data class SeriesPoint(val date: LocalDate, val value: Double)

/** A value against its previous comparable value, so a figure can be shown with its direction. */
data class Trend(val current: Double, val previous: Double?) {
    val delta: Double? get() = previous?.let { current - it }
    val rising: Boolean get() = (delta ?: 0.0) > 0.0001
    val falling: Boolean get() = (delta ?: 0.0) < -0.0001
    val pctChange: Double?
        get() = previous?.takeIf { abs(it) > 0.0001 }?.let { (current - it) / abs(it) * 100.0 }
}

enum class PrType(val display: String) {
    WEIGHT("Heaviest weight"),
    REPS("Most reps"),
    E1RM("Best estimated 1RM"),
    VOLUME("Best set volume"),
}

data class PersonalRecord(
    val type: PrType,
    val exerciseId: String,
    val exerciseName: String,
    val value: Double,
    val date: LocalDate,
    val sessionId: String,
    val previous: Double?,
)

data class ExerciseStats(
    val exerciseId: String,
    val name: String,
    val muscle: Muscle,
    val timesPerformed: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val firstTopWeightKg: Double,
    val latestTopWeightKg: Double,
    val heaviestKg: Double,
    val bestReps: Int,
    val bestSetVolumeKg: Double,
    val bestE1rmKg: Double,
    val totalVolumeKg: Double,
    val totalReps: Int,
    val totalSets: Int,
    val topWeightSeries: List<SeriesPoint>,
    val e1rmSeries: List<SeriesPoint>,
    val volumeSeries: List<SeriesPoint>,
) {
    /** Load added since the movement was first performed. The plainest measure of progress. */
    val weightGainKg: Double get() = latestTopWeightKg - firstTopWeightKg
}

data class WeekBucket(
    val weekStart: LocalDate,
    val volumeKg: Double,
    val sets: Int,
    val reps: Int,
    val sessions: Int,
)

data class MuscleVolume(val muscle: Muscle, val volumeKg: Double, val sets: Int, val share: Double)

data class ConsistencyStats(
    val totalSessions: Int,
    val sessionsThisWeek: Int,
    val sessionsThisMonth: Int,
    val weekStreak: Int,
    val longestWeekStreak: Int,
    val avgSessionsPerWeek: Double,
    val totalMinutes: Int,
    val activeDates: Set<LocalDate>,
)

data class MeasurementDelta(
    val key: String,
    val label: String,
    val firstCm: Double,
    val latestCm: Double,
) {
    val deltaCm: Double get() = latestCm - firstCm
}

data class BodyStats(
    val latestWeightKg: Double?,
    val weightTrend: Trend?,
    val latestBodyFatPct: Double?,
    val bodyFatTrend: Trend?,
    val leanMassKg: Double?,
    val leanMassChangeKg: Double?,
    val fatMassKg: Double?,
    val fatMassChangeKg: Double?,
    val weightSeries: List<SeriesPoint>,
    val bodyFatSeries: List<SeriesPoint>,
    val measurementDeltas: List<MeasurementDelta>,
    val entryCount: Int,
)

/**
 * Distance travelled toward one target.
 *
 * [fraction] is measured against the distance actually required, so a goal that moves downward
 * (bodyweight, body fat) and one that moves upward (a lift) both read as "how far along".
 */
data class GoalProgress(
    val label: String,
    val start: Double,
    val current: Double,
    val target: Double,
    val milestone: Double? = null,
    val unit: String = "kg",
) {
    val fraction: Double
        get() {
            val span = target - start
            if (abs(span) < 0.0001) return if (abs(current - target) < 0.0001) 1.0 else 0.0
            return ((current - start) / span).coerceIn(0.0, 1.0)
        }

    val remaining: Double get() = target - current

    val milestoneFraction: Double?
        get() {
            val m = milestone ?: return null
            val span = target - start
            if (abs(span) < 0.0001) return null
            return ((m - start) / span).coerceIn(0.0, 1.0)
        }
}

enum class RangeFilter(val label: String, val days: Int?) {
    W4("4 weeks", 28),
    W12("12 weeks", 84),
    YEAR("1 year", 365),
    ALL("All time", null),
}

/** Everything the dashboard renders, computed in one pass. */
data class Dashboard(
    val range: RangeFilter,
    val consistency: ConsistencyStats,
    val volumeKg: Double,
    val volumeTrend: Trend,
    val totalSets: Int,
    val totalReps: Int,
    val totalExercises: Int,
    val heaviestSetKg: Double,
    val weekBuckets: List<WeekBucket>,
    val muscleVolume: List<MuscleVolume>,
    val topLifts: List<ExerciseStats>,
    val recentPrs: List<PersonalRecord>,
    val goalProgress: List<GoalProgress>,
    val strengthIndexSeries: List<SeriesPoint>,
    val body: BodyStats,
    val sessionCount: Int,
)

// ---------------------------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------------------------

/**
 * Monday-first list of weekdays.
 *
 * `DayOfWeek` is a Java enum, so this avoids depending on Kotlin's `entries` extending to Java
 * enums, and it fixes the display order in one place rather than at every call site.
 */
val WeekDays: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

object Analytics {

    /**
     * Epley estimated one-rep max: `weight x (1 + reps / 30)`.
     *
     * A single rep returns the weight itself. It is an ESTIMATE and nothing in this app should
     * present it otherwise -- the formula drifts badly at high rep counts, which is what
     * [e1rmReliable] exists to flag.
     */
    fun e1rm(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || weightKg <= 0.0) return 0.0
        if (reps == 1) return weightKg
        return weightKg * (1.0 + reps / 30.0)
    }

    fun e1rmReliable(reps: Int): Boolean = reps in 1..12

    /** Monday of the ISO week containing [date]. Locale-independent by construction. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    // -- selection -----------------------------------------------------------------------------

    fun sessionsIn(sessions: List<Session>, range: RangeFilter, today: LocalDate): List<Session> {
        val days = range.days ?: return sessions
        val from = today.minusDays((days - 1).toLong())
        return sessions.filter { !it.date.isBefore(from) }
    }

    // -- consistency ---------------------------------------------------------------------------

    fun consistency(sessions: List<Session>, today: LocalDate): ConsistencyStats {
        val finished = sessions.filter { it.finished }
        val dates = finished.map { it.date }.toSet()
        val thisWeekStart = weekStart(today)
        val monthStart = today.withDayOfMonth(1)

        val weeks = finished.map { weekStart(it.date) }.toSortedSet()

        // Streaks count WEEKS, not days. The programme prescribes a rest day, so a day-based
        // streak would reset every week by design and punish correct adherence.
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (w in weeks) {
            run = if (previous != null && ChronoUnit.WEEKS.between(previous, w) == 1L) run + 1 else 1
            if (run > longest) longest = run
            previous = w
        }

        // The current streak may legitimately end on last week: this week is not over yet, so an
        // unbroken run that has not yet been continued must not be reported as broken.
        var current = 0
        var cursor = if (weeks.contains(thisWeekStart)) thisWeekStart else thisWeekStart.minusWeeks(1)
        while (weeks.contains(cursor)) {
            current++
            cursor = cursor.minusWeeks(1)
        }

        val span = finished.minByOrNull { it.date }?.date?.let {
            maxOf(1L, ChronoUnit.WEEKS.between(weekStart(it), thisWeekStart) + 1)
        } ?: 1L

        return ConsistencyStats(
            totalSessions = finished.size,
            sessionsThisWeek = finished.count { !it.date.isBefore(thisWeekStart) },
            sessionsThisMonth = finished.count { !it.date.isBefore(monthStart) },
            weekStreak = current,
            longestWeekStreak = longest,
            avgSessionsPerWeek = finished.size.toDouble() / span.toDouble(),
            totalMinutes = finished.sumOf { it.durationMinutes ?: 0 },
            activeDates = dates,
        )
    }

    // -- volume --------------------------------------------------------------------------------

    fun weekBuckets(sessions: List<Session>, weeks: Int, today: LocalDate): List<WeekBucket> {
        val thisWeek = weekStart(today)
        val starts = (0 until weeks).map { thisWeek.minusWeeks((weeks - 1 - it).toLong()) }
        val grouped = sessions.filter { it.finished }.groupBy { weekStart(it.date) }
        return starts.map { start ->
            val inWeek = grouped[start].orEmpty()
            WeekBucket(
                weekStart = start,
                volumeKg = inWeek.sumOf { it.volumeKg },
                sets = inWeek.sumOf { it.workingSetCount },
                reps = inWeek.sumOf { it.totalReps },
                sessions = inWeek.size,
            )
        }
    }

    fun muscleVolume(sessions: List<Session>, catalogue: Map<String, Exercise>): List<MuscleVolume> {
        val volumes = mutableMapOf<Muscle, Double>()
        val setCounts = mutableMapOf<Muscle, Int>()
        for (session in sessions.filter { it.finished }) {
            for (ex in session.exercises) {
                val muscle = catalogue[ex.exerciseId]?.muscle ?: Muscle.OTHER
                volumes[muscle] = (volumes[muscle] ?: 0.0) + ex.volumeKg
                setCounts[muscle] = (setCounts[muscle] ?: 0) + ex.workingSets.size
            }
        }
        val total = volumes.values.sum()
        return volumes.entries
            .filter { it.value > 0.0 || (setCounts[it.key] ?: 0) > 0 }
            .map { (muscle, volume) ->
                MuscleVolume(
                    muscle = muscle,
                    volumeKg = volume,
                    sets = setCounts[muscle] ?: 0,
                    share = if (total > 0.0) volume / total else 0.0,
                )
            }
            .sortedByDescending { it.volumeKg }
    }

    // -- per exercise --------------------------------------------------------------------------

    fun exerciseStats(
        exerciseId: String,
        sessions: List<Session>,
        catalogue: Map<String, Exercise>,
    ): ExerciseStats? {
        val exercise = catalogue[exerciseId] ?: return null
        val performances = sessions
            .filter { it.finished }
            .sortedBy { it.date }
            .mapNotNull { session ->
                val entry = session.exercises.firstOrNull { it.exerciseId == exerciseId && it.workingSets.isNotEmpty() }
                entry?.let { session.date to it }
            }
        if (performances.isEmpty()) return null

        val allSets = performances.flatMap { it.second.workingSets }

        return ExerciseStats(
            exerciseId = exerciseId,
            name = exercise.name,
            muscle = exercise.muscle,
            timesPerformed = performances.size,
            firstDate = performances.first().first,
            lastDate = performances.last().first,
            firstTopWeightKg = performances.first().second.topWeightKg,
            latestTopWeightKg = performances.last().second.topWeightKg,
            heaviestKg = allSets.maxOfOrNull { it.weightKg } ?: 0.0,
            bestReps = allSets.maxOfOrNull { it.reps } ?: 0,
            bestSetVolumeKg = allSets.maxOfOrNull { it.volumeKg } ?: 0.0,
            bestE1rmKg = allSets.maxOfOrNull { e1rm(it.weightKg, it.reps) } ?: 0.0,
            totalVolumeKg = allSets.sumOf { it.volumeKg },
            totalReps = allSets.sumOf { it.reps },
            totalSets = allSets.size,
            topWeightSeries = performances.map { SeriesPoint(it.first, it.second.topWeightKg) },
            e1rmSeries = performances.map { (date, entry) ->
                SeriesPoint(date, entry.workingSets.maxOfOrNull { e1rm(it.weightKg, it.reps) } ?: 0.0)
            },
            volumeSeries = performances.map { SeriesPoint(it.first, it.second.volumeKg) },
        )
    }

    fun allExerciseStats(
        sessions: List<Session>,
        catalogue: Map<String, Exercise>,
    ): List<ExerciseStats> {
        val ids = sessions.filter { it.finished }.flatMap { s -> s.exercises.map { it.exerciseId } }.distinct()
        return ids.mapNotNull { exerciseStats(it, sessions, catalogue) }
    }

    // -- personal records ----------------------------------------------------------------------

    /**
     * Records set by [session], judged against everything performed strictly before it.
     *
     * A first-ever performance is not a record. Calling it one would mean every new movement
     * fires four celebrations on day one, which trains the user to ignore them.
     */
    fun recordsFor(
        session: Session,
        history: List<Session>,
        catalogue: Map<String, Exercise>,
    ): List<PersonalRecord> {
        if (!session.finished) return emptyList()
        val prior = history.filter { it.finished && it.id != session.id && it.date.isBefore(session.date) }
        val records = mutableListOf<PersonalRecord>()

        for (entry in session.exercises) {
            val sets = entry.workingSets
            if (sets.isEmpty()) continue
            val name = catalogue[entry.exerciseId]?.name ?: continue

            val priorSets = prior.flatMap { s -> s.exercises.filter { it.exerciseId == entry.exerciseId } }
                .flatMap { it.workingSets }
            if (priorSets.isEmpty()) continue

            fun consider(type: PrType, now: Double, before: Double) {
                if (now > before + 0.0001) {
                    records += PersonalRecord(
                        type = type,
                        exerciseId = entry.exerciseId,
                        exerciseName = name,
                        value = now,
                        date = session.date,
                        sessionId = session.id,
                        previous = before,
                    )
                }
            }

            consider(PrType.WEIGHT, sets.maxOf { it.weightKg }, priorSets.maxOf { it.weightKg })
            consider(PrType.E1RM, sets.maxOf { e1rm(it.weightKg, it.reps) }, priorSets.maxOf { e1rm(it.weightKg, it.reps) })
            consider(PrType.VOLUME, sets.maxOf { it.volumeKg }, priorSets.maxOf { it.volumeKg })
            consider(PrType.REPS, sets.maxOf { it.reps }.toDouble(), priorSets.maxOf { it.reps }.toDouble())
        }
        return records
    }

    fun allRecords(sessions: List<Session>, catalogue: Map<String, Exercise>): List<PersonalRecord> {
        val finished = sessions.filter { it.finished }.sortedBy { it.date }
        return finished.flatMap { recordsFor(it, finished, catalogue) }.sortedByDescending { it.date }
    }

    // -- body ----------------------------------------------------------------------------------

    fun bodyStats(entries: List<BodyEntry>): BodyStats {
        val sorted = entries.sortedBy { it.date }
        val weights = sorted.filter { it.weightKg != null }
        val fats = sorted.filter { it.bodyFatPct != null }

        val latestWeight = weights.lastOrNull()?.weightKg
        val previousWeight = weights.dropLast(1).lastOrNull()?.weightKg
        val latestFat = fats.lastOrNull()?.bodyFatPct
        val previousFat = fats.dropLast(1).lastOrNull()?.bodyFatPct

        // Composition is only ever computed from entries carrying BOTH weight and body fat.
        // Pairing a weight from one date with a body-fat reading from another would invent a
        // change that was never measured.
        val complete = sorted.filter { it.weightKg != null && it.bodyFatPct != null }
        val firstComplete = complete.firstOrNull()
        val lastComplete = complete.lastOrNull()

        val sites = sorted.flatMap { it.measurementsCm.keys }.distinct()
        val deltas = sites.mapNotNull { key ->
            val withSite = sorted.filter { it.measurementsCm.containsKey(key) }
            if (withSite.size < 2) return@mapNotNull null
            MeasurementDelta(
                key = key,
                label = MeasurementSites.label(key),
                firstCm = withSite.first().measurementsCm.getValue(key),
                latestCm = withSite.last().measurementsCm.getValue(key),
            )
        }

        return BodyStats(
            latestWeightKg = latestWeight,
            weightTrend = latestWeight?.let { Trend(it, previousWeight) },
            latestBodyFatPct = latestFat,
            bodyFatTrend = latestFat?.let { Trend(it, previousFat) },
            leanMassKg = lastComplete?.leanMassKg,
            leanMassChangeKg = if (complete.size >= 2) {
                (lastComplete?.leanMassKg ?: 0.0) - (firstComplete?.leanMassKg ?: 0.0)
            } else null,
            fatMassKg = lastComplete?.fatMassKg,
            fatMassChangeKg = if (complete.size >= 2) {
                (lastComplete?.fatMassKg ?: 0.0) - (firstComplete?.fatMassKg ?: 0.0)
            } else null,
            weightSeries = weights.map { SeriesPoint(it.date, it.weightKg!!) },
            bodyFatSeries = fats.map { SeriesPoint(it.date, it.bodyFatPct!!) },
            measurementDeltas = deltas,
            entryCount = sorted.size,
        )
    }

    // -- goals ---------------------------------------------------------------------------------

    fun goalProgress(data: AppData): List<GoalProgress> {
        val out = mutableListOf<GoalProgress>()
        val goals = data.goals

        val startWeight = goals.startWeightKg
        val targetWeight = goals.targetWeightKg
        val currentWeight = data.latestWeightKg
        if (startWeight != null && targetWeight != null && currentWeight != null) {
            out += GoalProgress("Bodyweight", startWeight, currentWeight, targetWeight, unit = "kg")
        }

        val startBf = goals.startBodyFatPct
        val targetBf = goals.targetBodyFatPct
        val currentBf = data.latestBodyFatPct
        if (startBf != null && targetBf != null && currentBf != null) {
            out += GoalProgress("Body fat", startBf, currentBf, targetBf, unit = "%")
        }

        val finished = data.sessions.filter { it.finished }
        val catalogue = data.exerciseById
        for (goal in goals.liftGoals) {
            val stats = exerciseStats(goal.exerciseId, finished, catalogue)
            val current = stats?.latestTopWeightKg?.takeIf { it > 0.0 } ?: goal.startKg
            out += GoalProgress(
                label = goal.label.ifBlank { catalogue[goal.exerciseId]?.name ?: "Lift" },
                start = goal.startKg,
                current = current,
                target = goal.targetKg,
                milestone = goal.milestoneKg,
                unit = "kg",
            )
        }
        return out
    }

    /**
     * A single strength number per week: the summed best estimated 1RM across every movement
     * trained that week, indexed to 100 at the first week with data.
     *
     * It answers "am I getting stronger overall" in one line. It is an index, not a load -- it
     * moves when exercise selection changes, which is why it is drawn as a trend and never
     * quoted as a weight.
     */
    fun strengthIndex(sessions: List<Session>): List<SeriesPoint> {
        val finished = sessions.filter { it.finished }.sortedBy { it.date }
        if (finished.isEmpty()) return emptyList()

        val byWeek = finished.groupBy { weekStart(it.date) }.toSortedMap()
        val raw = byWeek.map { (week, weekSessions) ->
            val best = weekSessions
                .flatMap { it.exercises }
                .groupBy { it.exerciseId }
                .mapNotNull { (_, entries) ->
                    entries.flatMap { it.workingSets }.maxOfOrNull { e1rm(it.weightKg, it.reps) }
                }
            week to best.sum()
        }.filter { it.second > 0.0 }

        val base = raw.firstOrNull()?.second ?: return emptyList()
        if (base <= 0.0) return emptyList()
        return raw.map { SeriesPoint(it.first, it.second / base * 100.0) }
    }

    // -- the whole dashboard -------------------------------------------------------------------

    fun dashboard(data: AppData, range: RangeFilter, today: LocalDate): Dashboard {
        val finished = data.sessions.filter { it.finished }
        val catalogue = data.exerciseById
        val inRange = sessionsIn(finished, range, today)

        // The comparison window is the same length, immediately before the current one, so a
        // trend arrow compares like with like rather than against all of history.
        val previous = range.days?.let { days ->
            val from = today.minusDays((days * 2 - 1).toLong())
            val to = today.minusDays(days.toLong())
            finished.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        } ?: emptyList()

        val weeks = when (range) {
            RangeFilter.W4 -> 4
            RangeFilter.W12 -> 12
            RangeFilter.YEAR -> 26
            RangeFilter.ALL -> 26
        }

        val stats = allExerciseStats(finished, catalogue)

        return Dashboard(
            range = range,
            consistency = consistency(finished, today),
            volumeKg = inRange.sumOf { it.volumeKg },
            volumeTrend = Trend(
                inRange.sumOf { it.volumeKg },
                previous.takeIf { it.isNotEmpty() }?.sumOf { it.volumeKg },
            ),
            totalSets = inRange.sumOf { it.workingSetCount },
            totalReps = inRange.sumOf { it.totalReps },
            totalExercises = inRange.flatMap { s -> s.exercises.map { it.exerciseId } }.distinct().size,
            heaviestSetKg = inRange.flatMap { s -> s.exercises.flatMap { it.workingSets } }
                .maxOfOrNull { it.weightKg } ?: 0.0,
            weekBuckets = weekBuckets(finished, weeks, today),
            muscleVolume = muscleVolume(inRange, catalogue),
            topLifts = stats.sortedByDescending { it.totalVolumeKg }.take(8),
            recentPrs = allRecords(finished, catalogue).take(12),
            goalProgress = goalProgress(data),
            strengthIndexSeries = strengthIndex(finished),
            body = bodyStats(data.body),
            sessionCount = inRange.size,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Formatting -- shared so a number never renders two different ways in two places
// ---------------------------------------------------------------------------------------------

object Fmt {
    private const val LB_PER_KG = 2.2046226218

    fun toDisplayWeight(kg: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.LB) kg * LB_PER_KG else kg

    fun fromDisplayWeight(value: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.LB) value / LB_PER_KG else value

    /** Drops a trailing ".0" so 20 kg reads as "20", while 17.5 keeps its half. */
    fun trim(value: Double): String {
        val rounded = (value * 100.0).roundToInt() / 100.0
        return if (abs(rounded - rounded.roundToInt()) < 0.001) {
            rounded.roundToInt().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }

    fun weight(kg: Double, unit: WeightUnit): String = trim(toDisplayWeight(kg, unit))

    fun weightWithUnit(kg: Double, unit: WeightUnit): String = "${weight(kg, unit)} ${unit.suffix}"

    /** Large tonnage figures become unreadable in full; above ten tonnes they are abbreviated. */
    fun volume(kg: Double, unit: WeightUnit): String {
        val v = toDisplayWeight(kg, unit)
        return when {
            v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000)
            v >= 10_000 -> String.format("%.1fk", v / 1_000)
            else -> trim(v)
        }
    }

    fun signed(value: Double, decimals: Int = 1): String {
        val rounded = if (decimals == 0) {
            value.roundToInt().toString()
        } else {
            String.format("%.${decimals}f", value)
        }
        return if (value > 0) "+$rounded" else rounded
    }

    fun percent(value: Double, decimals: Int = 0): String = String.format("%.${decimals}f%%", value)

    fun duration(minutes: Int): String {
        if (minutes < 60) return "${minutes}m"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    fun clock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return String.format("%d:%02d", safe / 60, safe % 60)
    }

    fun dayLabel(day: DayOfWeek): String =
        day.name.lowercase().replaceFirstChar { it.uppercase() }

    fun shortDay(day: DayOfWeek): String = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
}
