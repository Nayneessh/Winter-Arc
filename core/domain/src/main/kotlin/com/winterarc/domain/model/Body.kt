package com.winterarc.domain.model

import java.time.LocalDate

enum class WeightUnit { KG, LB;
    val label: String get() = if (this == KG) "kg" else "lb"
}

enum class LengthUnit { CM, IN;
    val label: String get() = if (this == CM) "cm" else "in"
}

object UnitConversion {
    private const val LB_PER_KG = 2.2046226218
    private const val CM_PER_IN = 2.54

    fun kgToLb(kg: Double) = kg * LB_PER_KG
    fun lbToKg(lb: Double) = lb / LB_PER_KG
    fun cmToIn(cm: Double) = cm / CM_PER_IN
    fun inToCm(inches: Double) = inches * CM_PER_IN

    fun weightFromKg(kg: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) kg else kgToLb(kg)
    fun weightToKg(value: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) value else lbToKg(value)
    fun lengthFromCm(cm: Double, unit: LengthUnit) = if (unit == LengthUnit.CM) cm else cmToIn(cm)
    fun lengthToCm(value: Double, unit: LengthUnit) = if (unit == LengthUnit.CM) value else inToCm(value)
}

/**
 * A body check-in. All lengths are stored canonically in CENTIMETRES and weight in KILOGRAMS;
 * display units are a presentation concern only. This keeps history comparable when the user
 * switches units mid-block.
 */
data class BodyMetric(
    val id: String,
    val date: LocalDate,
    val weightKg: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val bicepsLeftCm: Double? = null,
    val bicepsRightCm: Double? = null,
    val shouldersCm: Double? = null,
    val thighCm: Double? = null,
    val calfCm: Double? = null,
    val neckCm: Double? = null,
    val forearmCm: Double? = null,
    val bodyFatPercent: Double? = null,
    val notes: String? = null,
) {
    /** Fat mass in kg, only computable when BOTH weight and a body-fat reading exist. */
    val fatMassKg: Double? get() =
        if (weightKg != null && bodyFatPercent != null) weightKg * (bodyFatPercent / 100.0) else null

    /** Lean mass in kg. Same precondition as [fatMassKg]. */
    val leanMassKg: Double? get() =
        if (weightKg != null && fatMassKg != null) weightKg - fatMassKg!! else null
}

/** Which measurement a chart or delta refers to. */
enum class MetricField(val label: String, val isWeight: Boolean) {
    WEIGHT("Weight", true),
    CHEST("Chest", false),
    WAIST("Waist", false),
    BICEPS_LEFT("Biceps (L)", false),
    BICEPS_RIGHT("Biceps (R)", false),
    SHOULDERS("Shoulders", false),
    THIGH("Thigh", false),
    CALF("Calf", false),
    NECK("Neck", false),
    FOREARM("Forearm", false),
    BODY_FAT("Body fat %", false);

    fun read(m: BodyMetric): Double? = when (this) {
        WEIGHT -> m.weightKg
        CHEST -> m.chestCm
        WAIST -> m.waistCm
        BICEPS_LEFT -> m.bicepsLeftCm
        BICEPS_RIGHT -> m.bicepsRightCm
        SHOULDERS -> m.shouldersCm
        THIGH -> m.thighCm
        CALF -> m.calfCm
        NECK -> m.neckCm
        FOREARM -> m.forearmCm
        BODY_FAT -> m.bodyFatPercent
    }
}

/**
 * Change in a single measurement between two check-ins.
 *
 * Deliberately neutral: this type reports WHAT CHANGED, never what tissue the change was.
 * See [com.winterarc.domain.analytics.BodyAnalytics] for the reasoning.
 */
data class MetricDelta(
    val field: MetricField,
    val startValue: Double,
    val currentValue: Double,
    val startDate: LocalDate,
    val currentDate: LocalDate,
) {
    val change: Double get() = currentValue - startValue
    val percentChange: Double? get() =
        if (startValue == 0.0) null else (change / startValue) * 100.0
    val hasIncreased: Boolean get() = change > 0
}
