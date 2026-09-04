package com.winterarc.domain

import com.google.common.truth.Truth.assertThat
import com.winterarc.domain.model.DayType
import com.winterarc.domain.seed.WinterArcSeed
import org.junit.Test

class SeedTest {

    private val allTemplates = WinterArcSeed.programmes.flatMap { it.templates }
    private val allPlanned = allTemplates.flatMap { it.exercises }

    @Test
    fun `every planned exercise references a real library entry`() {
        val libraryIds = WinterArcSeed.exercises.map { it.id }.toSet()
        val dangling = allPlanned.map { it.exerciseId }.filterNot { it in libraryIds }

        assertThat(dangling).isEmpty()
    }

    @Test
    fun `ids are unique across exercises templates and planned entries`() {
        assertThat(WinterArcSeed.exercises.map { it.id })
            .containsNoDuplicates()
        assertThat(allTemplates.map { it.id }).containsNoDuplicates()
        assertThat(allPlanned.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `exactly one programme is active`() {
        assertThat(WinterArcSeed.programmes.count { it.isActive }).isEqualTo(1)
        assertThat(WinterArcSeed.activeProgramme.id).isEqualTo("prog-v2")
    }

    @Test
    fun `the active programme covers all seven days with three lifting days`() {
        val v2 = WinterArcSeed.activeProgramme.templates
        assertThat(v2.mapNotNull { it.dayOfWeek }.sorted()).isEqualTo(listOf(1, 2, 3, 4, 5, 6, 7))

        // Tue, Thu, Sat are the lifting days; Monday's pull-up slot is a 20-minute add-on.
        val bigDays = v2.filter { it.dayType == DayType.TRAINING && it.plannedSetCount >= 15 }
        assertThat(bigDays.mapNotNull { it.dayOfWeek }).containsExactly(2, 4, 6)
    }

    @Test
    fun `rest and MMA days carry no prescribed exercises`() {
        val nonLifting = allTemplates.filter { it.dayType == DayType.REST || it.dayType == DayType.MMA }

        assertThat(nonLifting).isNotEmpty()
        assertThat(nonLifting.all { it.exercises.isEmpty() }).isTrue()
    }

    @Test
    fun `rep ranges are coherent and set counts are positive`() {
        allPlanned.forEach { p ->
            assertThat(p.repLow).isAtLeast(1)
            assertThat(p.repHigh).isAtLeast(p.repLow)
            assertThat(p.sets).isAtLeast(1)
            assertThat(p.restSeconds).isAtLeast(15)
        }
    }

    @Test
    fun `positions within a template are contiguous from zero`() {
        allTemplates.filter { it.exercises.isNotEmpty() }.forEach { t ->
            assertThat(t.exercises.map { it.position }.sorted())
                .isEqualTo(t.exercises.indices.toList())
        }
    }

    @Test
    fun `superset groups pair at least two movements`() {
        allTemplates.forEach { t ->
            t.exercises
                .filter { it.supersetGroup != null }
                .groupBy { it.supersetGroup }
                .forEach { (group, members) ->
                    assertThat(members.size).isAtLeast(2)
                    assertThat(group).isNotNull()
                }
        }
    }

    @Test
    fun `the active programme preserves the arm and back priority`() {
        val v2Sets = WinterArcSeed.activeProgramme.templates.flatMap { it.exercises }
        val byExercise = WinterArcSeed.exercises.associateBy { it.id }

        fun setsFor(muscle: com.winterarc.domain.model.MuscleGroup) =
            v2Sets.filter { byExercise[it.exerciseId]?.primaryMuscle == muscle }.sumOf { it.sets }

        // The workbook's stated V2 allocation: biceps 15 direct, triceps 9 direct.
        assertThat(setsFor(com.winterarc.domain.model.MuscleGroup.BICEPS)).isEqualTo(15)
        assertThat(setsFor(com.winterarc.domain.model.MuscleGroup.TRICEPS)).isEqualTo(9)
        // Back across pull-ups (Mon + Thu), rows and pulldowns.
        assertThat(setsFor(com.winterarc.domain.model.MuscleGroup.BACK)).isAtLeast(17)
    }

    @Test
    fun `baseline measurements convert from the stated imperial figures`() {
        assertThat(WinterArcSeed.Baseline.WEIGHT_KG).isEqualTo(80.5)
        assertThat(WinterArcSeed.Baseline.CHEST_CM).isWithin(0.01).of(106.68)
        assertThat(WinterArcSeed.Baseline.WAIST_CM).isWithin(0.01).of(96.52)
        assertThat(WinterArcSeed.Baseline.BICEPS_CM).isWithin(0.01).of(40.64)
        assertThat(WinterArcSeed.Baseline.HEIGHT_CM).isWithin(0.01).of(170.18)
    }

    @Test
    fun `a session can be started from every seeded lifting template`() {
        val engine = testEngine()
        WinterArcSeed.activeProgramme.templates
            .filter { it.exercises.isNotEmpty() }
            .forEach { t ->
                val s = engine.startFromTemplate(t)
                assertThat(s.exercises).hasSize(t.exercises.size)
                assertThat(s.plannedSetTotal).isEqualTo(t.plannedSetCount)
            }
    }
}
