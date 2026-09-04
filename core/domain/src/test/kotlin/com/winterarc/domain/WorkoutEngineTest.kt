package com.winterarc.domain

import com.google.common.truth.Truth.assertThat
import com.winterarc.domain.model.SessionStatus
import com.winterarc.domain.programme.WorkoutEngine
import org.junit.Test
import org.junit.Assert.assertThrows

class WorkoutEngineTest {

    private val bench = "ex-bench"
    private val fly = "ex-fly"
    private val dbPress = "ex-db-press"

    @Test
    fun `starting from a template snapshots the prescribed plan`() {
        val engine = testEngine()
        val tpl = template(planned("p1", bench, 0, sets = 3, repLow = 10, repHigh = 10, weight = 70.0))

        val session = engine.startFromTemplate(tpl)

        val pe = session.exercises.single()
        assertThat(pe.plannedSets).isEqualTo(3)
        assertThat(pe.plannedRepLow).isEqualTo(10)
        assertThat(pe.plannedWeightKg).isEqualTo(70.0)
        assertThat(session.status).isEqualTo(SessionStatus.IN_PROGRESS)
    }

    /** The headline scenario from the brief: plan 3x10x70, actually perform four heavier sets. */
    @Test
    fun `extra sets are preserved and reported as extra without altering the plan`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(
            template(planned("p1", bench, 0, sets = 3, repLow = 10, repHigh = 10, weight = 70.0)),
        )
        val peId = s.exercises.single().id

        s = engine.logSet(s, peId, 70.0, 10)
        s = engine.logSet(s, peId, 75.0, 10)
        s = engine.logSet(s, peId, 80.0, 8)
        s = engine.logSet(s, peId, 80.0, 6)

        val pe = s.exercises.single()
        assertThat(pe.plannedSets).isEqualTo(3)
        assertThat(pe.actualSetCount).isEqualTo(4)
        assertThat(pe.extraSetCount).isEqualTo(1)
        assertThat(pe.plannedWeightKg).isEqualTo(70.0)
        assertThat(pe.totalReps).isEqualTo(34)
        // 700 + 750 + 640 + 480
        assertThat(pe.volumeKg).isEqualTo(2570.0)
    }

    @Test
    fun `performing more reps than prescribed does not overwrite the prescription`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(
            template(planned("p1", bench, 0, sets = 1, repLow = 10, repHigh = 10)),
        )
        val peId = s.exercises.single().id
        s = engine.logSet(s, peId, 60.0, 12)

        val pe = s.exercises.single()
        assertThat(pe.plannedRepHigh).isEqualTo(10)
        assertThat(pe.workingSets.single().reps).isEqualTo(12)
    }

    @Test
    fun `missed sets are reported when fewer sets are performed than planned`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0, sets = 4)))
        val peId = s.exercises.single().id
        s = engine.logSet(s, peId, 60.0, 10)

        val pe = s.exercises.single()
        assertThat(pe.missedSetCount).isEqualTo(3)
        assertThat(pe.extraSetCount).isEqualTo(0)
    }

    @Test
    fun `warm-up sets are excluded from volume and set counts`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0, sets = 2)))
        val peId = s.exercises.single().id
        s = engine.logSet(s, peId, 20.0, 15, isWarmup = true)
        s = engine.logSet(s, peId, 80.0, 8)

        val pe = s.exercises.single()
        assertThat(pe.actualSetCount).isEqualTo(1)
        assertThat(pe.volumeKg).isEqualTo(640.0)
    }

    @Test
    fun `a zero-rep set contributes no volume and is not a working set`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0, sets = 2)))
        val peId = s.exercises.single().id
        s = engine.logSet(s, peId, 100.0, 0)

        val pe = s.exercises.single()
        assertThat(pe.volumeKg).isEqualTo(0.0)
        assertThat(pe.actualSetCount).isEqualTo(0)
    }

    @Test
    fun `ten extra sets are all retained`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0, sets = 3)))
        val peId = s.exercises.single().id
        repeat(13) { s = engine.logSet(s, peId, 50.0, 10) }

        val pe = s.exercises.single()
        assertThat(pe.actualSetCount).isEqualTo(13)
        assertThat(pe.extraSetCount).isEqualTo(10)
        assertThat(pe.sets.map { it.setNumber }).isEqualTo((1..13).toList())
    }

    @Test
    fun `decimal weights are preserved exactly`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0)))
        val peId = s.exercises.single().id
        s = engine.logSet(s, peId, 17.5, 8)

        assertThat(s.exercises.single().workingSets.single().weightKg).isEqualTo(17.5)
        assertThat(s.exercises.single().volumeKg).isEqualTo(140.0)
    }

    @Test
    fun `negative weight or reps are rejected`() {
        val engine = testEngine()
        val s = engine.startFromTemplate(template(planned("p1", bench, 0)))
        val peId = s.exercises.single().id

        assertThrows(IllegalArgumentException::class.java) { engine.logSet(s, peId, -5.0, 8) }
        assertThrows(IllegalArgumentException::class.java) { engine.logSet(s, peId, 50.0, -1) }
    }

    @Test
    fun `removing a set renumbers the remaining sets contiguously`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0, sets = 3)))
        val peId = s.exercises.single().id
        s = engine.logSet(s, peId, 60.0, 10)
        s = engine.logSet(s, peId, 65.0, 9)
        s = engine.logSet(s, peId, 70.0, 8)

        val middle = s.exercises.single().sets[1].id
        s = engine.removeSet(s, middle)

        val pe = s.exercises.single()
        assertThat(pe.sets.map { it.setNumber }).isEqualTo(listOf(1, 2))
        assertThat(pe.sets.map { it.weightKg }).isEqualTo(listOf(60.0, 70.0))
    }

    @Test
    fun `replacing an exercise records what was prescribed and clears the previous sets`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0, sets = 3, weight = 70.0)))
        val peId = s.exercises.single().id
        s = engine.logSet(s, peId, 70.0, 10)

        s = engine.replaceExercise(s, peId, dbPress)

        val pe = s.exercises.single()
        assertThat(pe.exerciseId).isEqualTo(dbPress)
        assertThat(pe.replacedExerciseId).isEqualTo(bench)
        assertThat(pe.wasReplaced).isTrue()
        assertThat(pe.sets).isEmpty()
        // The prescription survives the swap.
        assertThat(pe.plannedSets).isEqualTo(3)
        assertThat(pe.plannedWeightKg).isEqualTo(70.0)
    }

    @Test
    fun `replacing twice still points back at the originally prescribed exercise`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0)))
        val peId = s.exercises.single().id

        s = engine.replaceExercise(s, peId, dbPress)
        s = engine.replaceExercise(s, peId, fly)

        assertThat(s.exercises.single().replacedExerciseId).isEqualTo(bench)
        assertThat(s.exercises.single().exerciseId).isEqualTo(fly)
    }

    @Test
    fun `an ad hoc exercise can be added mid-session and is flagged as such`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0)))
        s = engine.addExercise(s, "ex-new", plannedSets = 4, plannedRepLow = 6, plannedRepHigh = 8)

        val added = s.exercises.last()
        assertThat(added.isAdHoc).isTrue()
        assertThat(added.exerciseId).isEqualTo("ex-new")
        assertThat(added.position).isEqualTo(1)
        assertThat(added.plannedSets).isEqualTo(4)
    }

    @Test
    fun `reordering rewrites positions and is rejected when ids do not match`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(
            template(
                planned("p1", bench, 0),
                planned("p2", fly, 1),
                planned("p3", dbPress, 2),
            ),
        )
        val ids = s.exercises.map { it.id }
        s = engine.reorderExercises(s, listOf(ids[2], ids[0], ids[1]))

        assertThat(s.exercises.sortedBy { it.position }.map { it.exerciseId })
            .isEqualTo(listOf(dbPress, bench, fly))

        assertThrows(IllegalArgumentException::class.java) {
            engine.reorderExercises(s, listOf(ids[0]))
        }
    }

    @Test
    fun `moveExercise performs a drag-and-drop style reorder`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(
            template(planned("p1", bench, 0), planned("p2", fly, 1), planned("p3", dbPress, 2)),
        )
        s = engine.moveExercise(s, fromIndex = 2, toIndex = 0)

        assertThat(s.exercises.sortedBy { it.position }.map { it.exerciseId })
            .isEqualTo(listOf(dbPress, bench, fly))
    }

    @Test
    fun `removing an exercise closes the gap in positions`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(
            template(planned("p1", bench, 0), planned("p2", fly, 1), planned("p3", dbPress, 2)),
        )
        val flyId = s.exercises[1].id
        s = engine.removeExercise(s, flyId)

        assertThat(s.exercises.map { it.position }).isEqualTo(listOf(0, 1))
        assertThat(s.exercises.map { it.exerciseId }).isEqualTo(listOf(bench, dbPress))
    }

    @Test
    fun `a skipped exercise keeps its prescription but leaves the performed totals`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(
            template(planned("p1", bench, 0, sets = 3), planned("p2", fly, 1, sets = 3)),
        )
        val flyId = s.exercises[1].id
        s = engine.logSet(s, s.exercises[0].id, 60.0, 10)
        s = engine.skipExercise(s, flyId)

        assertThat(s.performedExercises).hasSize(1)
        assertThat(s.plannedSetTotal).isEqualTo(6)
        assertThat(s.actualSetTotal).isEqualTo(1)
    }

    @Test
    fun `supersets can be created and broken`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0), planned("p2", fly, 1)))
        val ids = s.exercises.map { it.id }

        s = engine.setSupersetGroup(s, ids, "A")
        assertThat(s.exercises.map { it.supersetGroup }).containsExactly("A", "A")

        s = engine.setSupersetGroup(s, ids, null)
        assertThat(s.exercises.mapNotNull { it.supersetGroup }).isEmpty()
    }

    @Test
    fun `adjusting the plan mid-session changes only this session`() {
        val engine = testEngine()
        val tpl = template(planned("p1", bench, 0, sets = 3, weight = 70.0))
        var s = engine.startFromTemplate(tpl)
        s = engine.adjustPlan(s, s.exercises.single().id, plannedSets = 5, plannedWeightKg = 75.0)

        assertThat(s.exercises.single().plannedSets).isEqualTo(5)
        // The template object is untouched.
        assertThat(tpl.exercises.single().sets).isEqualTo(3)
        assertThat(tpl.exercises.single().targetWeightKg).isEqualTo(70.0)
    }

    @Test
    fun `a blank session supports training something completely different`() {
        val engine = testEngine()
        var s = engine.startBlank("Legs + Shoulders")
        s = engine.addExercise(s, "ex-squat", plannedSets = 4)
        s = engine.addExercise(s, "ex-ohp", plannedSets = 3)
        s = engine.logSet(s, s.exercises[0].id, 100.0, 5)

        assertThat(s.templateId).isNull()
        assertThat(s.name).isEqualTo("Legs + Shoulders")
        assertThat(s.exercises).hasSize(2)
        assertThat(s.totalVolumeKg).isEqualTo(500.0)
    }

    @Test
    fun `a completed session cannot be modified`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(template(planned("p1", bench, 0)))
        val peId = s.exercises.single().id
        s = engine.logSet(s, peId, 80.0, 8)
        val finished = engine.finish(s)

        assertThat(finished.status).isEqualTo(SessionStatus.COMPLETED)
        assertThrows(WorkoutEngine.SessionFrozen::class.java) {
            engine.logSet(finished, peId, 90.0, 8)
        }
        assertThrows(WorkoutEngine.SessionFrozen::class.java) {
            engine.replaceExercise(finished, peId, fly)
        }
        assertThrows(WorkoutEngine.SessionFrozen::class.java) {
            engine.removeExercise(finished, peId)
        }
    }

    @Test
    fun `session duration is derived from start and finish timestamps`() {
        val clock = FakeClock()
        val engine = testEngine(clock)
        var s = engine.startFromTemplate(template(planned("p1", bench, 0)))
        clock.advanceSeconds(72 * 60)
        s = engine.finish(s)

        assertThat(s.durationSeconds).isEqualTo(72 * 60)
    }

    @Test
    fun `session totals aggregate planned actual and extra sets across exercises`() {
        val engine = testEngine()
        var s = engine.startFromTemplate(
            template(planned("p1", bench, 0, sets = 3), planned("p2", fly, 1, sets = 3)),
        )
        val (a, b) = s.exercises.map { it.id }
        repeat(5) { s = engine.logSet(s, a, 60.0, 10) }
        repeat(2) { s = engine.logSet(s, b, 20.0, 15) }

        assertThat(s.plannedSetTotal).isEqualTo(6)
        assertThat(s.actualSetTotal).isEqualTo(7)
        assertThat(s.extraSetTotal).isEqualTo(2)
        assertThat(s.totalReps).isEqualTo(80)
        assertThat(s.totalVolumeKg).isEqualTo(3600.0)
    }
}
