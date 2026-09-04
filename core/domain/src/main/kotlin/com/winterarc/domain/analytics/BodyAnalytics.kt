package com.winterarc.domain.analytics

import com.winterarc.domain.model.BodyMetric
import com.winterarc.domain.model.MetricDelta
import com.winterarc.domain.model.MetricField
import kotlin.math.abs

/**
 * Body-composition reporting.
 *
 * EDITORIAL RULE ENFORCED IN CODE: scale weight is not body composition.
 *
 * A drop in bodyweight is reported strictly as a WEIGHT CHANGE. This type will not attribute
 * that change to fat, muscle or water. Fat-mass change is computed only when the user has
 * supplied body-fat percentages at BOTH ends of the comparison, and it is returned through
 * [FatMassEstimate], whose name and [FatMassEstimate.caveat] carry the uncertainty with it.
 * Likewise a measurement increase is reported as an increase in that measurement, never as
 * a quantity of muscle gained.
 */
object BodyAnalytics {

    fun delta(field: MetricField, entries: List<BodyMetric>): MetricDelta? {
        val points = entries
            .mapNotNull { m -> field.read(m)?.let { m.date to it } }
            .sortedBy { it.first }
        if (points.size < 2) return null
        val (startDate, startValue) = points.first()
        val (currentDate, currentValue) = points.last()
        return MetricDelta(field, startValue, currentValue, startDate, currentDate)
    }

    /** Latest recorded value for a field, or null if never recorded. */
    fun latest(field: MetricField, entries: List<BodyMetric>): Double? =
        entries.sortedByDescending { it.date }.firstNotNullOfOrNull { field.read(it) }

    /** All deltas the user actually has data for. Fields with fewer than two readings are omitted. */
    fun allDeltas(entries: List<BodyMetric>): List<MetricDelta> =
        MetricField.entries.mapNotNull { delta(it, entries) }

    /**
     * Estimated change in fat mass and lean mass between the first and last check-in that
     * BOTH carry a weight and a body-fat reading.
     *
     * Returns null when that precondition is not met. There is no fallback that guesses a
     * body-fat percentage: an unavailable estimate is reported as unavailable.
     */
    fun fatMassEstimate(entries: List<BodyMetric>): FatMassEstimate? {
        val usable = entries
            .filter { it.weightKg != null && it.bodyFatPercent != null }
            .sortedBy { it.date }
        if (usable.size < 2) return null
        val first = usable.first()
        val last = usable.last()
        val startFat = first.fatMassKg ?: return null
        val endFat = last.fatMassKg ?: return null
        val startLean = first.leanMassKg ?: return null
        val endLean = last.leanMassKg ?: return null
        return FatMassEstimate(
            startFatMassKg = startFat,
            currentFatMassKg = endFat,
            startLeanMassKg = startLean,
            currentLeanMassKg = endLean,
            startBodyFatPercent = first.bodyFatPercent!!,
            currentBodyFatPercent = last.bodyFatPercent!!,
        )
    }

    /**
     * Plain-language summary of a weight change that makes no claim about tissue.
     * Used verbatim by the UI so the wording cannot drift.
     */
    fun weightChangeStatement(delta: MetricDelta?): String = when {
        delta == null -> "Not enough data yet — log a second weigh-in."
        abs(delta.change) < 0.05 -> "Weight unchanged"
        delta.change < 0 -> "Weight change: ${delta.change.round1()} kg"
        else -> "Weight change: +${delta.change.round1()} kg"
    }

    /** Neutral wording for a circumference change. Reports the measurement, not the tissue. */
    fun measurementChangeStatement(delta: MetricDelta?, unitLabel: String): String = when {
        delta == null -> "Not enough data yet"
        abs(delta.change) < 0.05 -> "No change"
        delta.change > 0 -> "Measurement increased by ${delta.change.round1()} $unitLabel"
        else -> "Measurement decreased by ${abs(delta.change).round1()} $unitLabel"
    }
}

/**
 * Estimated fat/lean split change.
 *
 * Every field here is derived from a body-fat PERCENTAGE the user entered, and consumer-grade
 * body-fat measurement carries a meaningful error margin. Presented as an estimate, always.
 */
data class FatMassEstimate(
    val startFatMassKg: Double,
    val currentFatMassKg: Double,
    val startLeanMassKg: Double,
    val currentLeanMassKg: Double,
    val startBodyFatPercent: Double,
    val currentBodyFatPercent: Double,
) {
    val fatMassChangeKg: Double get() = currentFatMassKg - startFatMassKg
    val leanMassChangeKg: Double get() = currentLeanMassKg - startLeanMassKg
    val bodyFatPercentChange: Double get() = currentBodyFatPercent - startBodyFatPercent

    val caveat: String =
        "Estimated from the body-fat percentages you entered. Measurement error in consumer " +
            "body-fat readings is significant, so treat the direction of travel as more " +
            "reliable than the exact figure."
}
