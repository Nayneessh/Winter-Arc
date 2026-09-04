package com.winterarc.domain

import com.google.common.truth.Truth.assertThat
import com.winterarc.domain.analytics.BodyAnalytics
import com.winterarc.domain.analytics.round1
import com.winterarc.domain.model.BodyMetric
import com.winterarc.domain.model.LengthUnit
import com.winterarc.domain.model.MetricField
import com.winterarc.domain.model.UnitConversion
import com.winterarc.domain.model.WeightUnit
import org.junit.Test
import java.time.LocalDate

class BodyAnalyticsTest {

    private fun m(day: Int, weight: Double? = null, waist: Double? = null, bf: Double? = null) =
        BodyMetric(
            id = "m$day",
            date = LocalDate.of(2026, 9, day),
            weightKg = weight,
            waistCm = waist,
            bodyFatPercent = bf,
        )

    @Test
    fun `a single reading produces no delta`() {
        assertThat(BodyAnalytics.delta(MetricField.WEIGHT, listOf(m(1, weight = 80.5)))).isNull()
    }

    @Test
    fun `weight delta measures first to last reading`() {
        val d = BodyAnalytics.delta(
            MetricField.WEIGHT,
            listOf(m(1, weight = 80.5), m(15, weight = 79.0), m(30, weight = 77.5)),
        )!!

        assertThat(d.startValue).isEqualTo(80.5)
        assertThat(d.currentValue).isEqualTo(77.5)
        assertThat(d.change).isEqualTo(-3.0)
        assertThat(d.hasIncreased).isFalse()
    }

    @Test
    fun `readings are ordered by date not by list order`() {
        val d = BodyAnalytics.delta(
            MetricField.WEIGHT,
            listOf(m(30, weight = 77.5), m(1, weight = 80.5)),
        )!!

        assertThat(d.startValue).isEqualTo(80.5)
        assertThat(d.currentValue).isEqualTo(77.5)
    }

    @Test
    fun `entries missing the field are skipped rather than treated as zero`() {
        val d = BodyAnalytics.delta(
            MetricField.WEIGHT,
            listOf(m(1, weight = 80.5), m(10, waist = 95.0), m(20, weight = 79.0)),
        )!!

        assertThat(d.startValue).isEqualTo(80.5)
        assertThat(d.currentValue).isEqualTo(79.0)
    }

    /**
     * The central honesty requirement: losing 3 kg on the scale must never be described
     * as losing 3 kg of fat.
     */
    @Test
    fun `a weight drop is reported as weight change and never as fat lost`() {
        val d = BodyAnalytics.delta(
            MetricField.WEIGHT,
            listOf(m(1, weight = 80.5), m(30, weight = 77.5)),
        )
        val statement = BodyAnalytics.weightChangeStatement(d)

        assertThat(statement).isEqualTo("Weight change: -3.0 kg")
        assertThat(statement.lowercase()).doesNotContain("fat")
        assertThat(statement.lowercase()).doesNotContain("muscle")
    }

    @Test
    fun `a measurement increase is reported neutrally without claiming muscle gain`() {
        val d = BodyAnalytics.delta(
            MetricField.BICEPS_LEFT,
            listOf(
                BodyMetric(id = "a", date = LocalDate.of(2026, 9, 1), bicepsLeftCm = 40.6),
                BodyMetric(id = "b", date = LocalDate.of(2026, 12, 1), bicepsLeftCm = 43.2),
            ),
        )
        val statement = BodyAnalytics.measurementChangeStatement(d, "cm")

        assertThat(statement).isEqualTo("Measurement increased by 2.6 cm")
        assertThat(statement.lowercase()).doesNotContain("muscle")
        assertThat(statement.lowercase()).doesNotContain("kg")
    }

    @Test
    fun `fat mass estimate is unavailable without body fat readings`() {
        val entries = listOf(m(1, weight = 80.5), m(30, weight = 77.5))
        assertThat(BodyAnalytics.fatMassEstimate(entries)).isNull()
    }

    @Test
    fun `fat mass estimate needs two readings that each carry weight and body fat`() {
        val entries = listOf(m(1, weight = 80.5, bf = 25.0), m(30, weight = 77.5))
        assertThat(BodyAnalytics.fatMassEstimate(entries)).isNull()
    }

    @Test
    fun `fat mass estimate splits the change when body fat data exists`() {
        val entries = listOf(
            m(1, weight = 80.0, bf = 25.0),   // 20.0 kg fat, 60.0 kg lean
            m(30, weight = 77.0, bf = 22.0),  // 16.94 kg fat, 60.06 kg lean
        )
        val est = BodyAnalytics.fatMassEstimate(entries)!!

        assertThat(est.startFatMassKg).isWithin(0.001).of(20.0)
        assertThat(est.currentFatMassKg).isWithin(0.001).of(16.94)
        assertThat(est.fatMassChangeKg.round1()).isEqualTo(-3.1)
        // Lean mass essentially held while 3 kg came off the scale — the useful signal.
        assertThat(est.leanMassChangeKg).isWithin(0.1).of(0.06)
        assertThat(est.bodyFatPercentChange).isWithin(0.001).of(-3.0)
        assertThat(est.caveat).contains("Estimated")
    }

    @Test
    fun `no data yields an honest not-enough-data message rather than a zero`() {
        assertThat(BodyAnalytics.weightChangeStatement(null))
            .isEqualTo("Not enough data yet — log a second weigh-in.")
    }

    @Test
    fun `latest returns the most recent non-null reading`() {
        val entries = listOf(m(1, weight = 80.5), m(20, weight = 78.0), m(30, waist = 95.0))
        assertThat(BodyAnalytics.latest(MetricField.WEIGHT, entries)).isEqualTo(78.0)
    }
}

class UnitConversionTest {

    /** The user's stated baseline, converted to the canonical storage units. */
    @Test
    fun `the baseline measurements round-trip through canonical units`() {
        assertThat(UnitConversion.inToCm(42.0)).isWithin(0.001).of(106.68)
        assertThat(UnitConversion.inToCm(38.0)).isWithin(0.001).of(96.52)
        assertThat(UnitConversion.inToCm(16.0)).isWithin(0.001).of(40.64)
        assertThat(UnitConversion.cmToIn(106.68)).isWithin(0.001).of(42.0)
    }

    @Test
    fun `weight converts both ways without drift`() {
        val kg = 80.5
        val lb = UnitConversion.kgToLb(kg)
        assertThat(lb).isWithin(0.01).of(177.47)
        assertThat(UnitConversion.lbToKg(lb)).isWithin(0.0001).of(kg)
    }

    @Test
    fun `switching display units does not change the stored value`() {
        val storedKg = 80.5
        val shownInLb = UnitConversion.weightFromKg(storedKg, WeightUnit.LB)
        val backToKg = UnitConversion.weightToKg(shownInLb, WeightUnit.KG)

        assertThat(shownInLb).isWithin(0.01).of(177.47)
        // Re-reading in kg returns the canonical figure untouched.
        assertThat(UnitConversion.weightToKg(storedKg, WeightUnit.KG)).isEqualTo(storedKg)
        assertThat(backToKg).isWithin(0.01).of(177.47)
    }

    @Test
    fun `length converts both ways without drift`() {
        val cm = 106.68
        val inches = UnitConversion.lengthFromCm(cm, LengthUnit.IN)
        assertThat(inches).isWithin(0.0001).of(42.0)
        assertThat(UnitConversion.lengthToCm(inches, LengthUnit.IN)).isWithin(0.0001).of(cm)
    }
}
