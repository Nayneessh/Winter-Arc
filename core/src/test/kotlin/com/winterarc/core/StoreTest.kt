package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = FileStore(File(folder.root, "winter-arc.json"))

    @Test
    fun `a full state survives a save and load unchanged`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, 1_000L)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.updateSet(data, ex.id, ex.sets.first().id, weightKg = 17.5, reps = 9, done = true)
        data = Actions.finishSession(data, 2_000L)
        data = Actions.saveBodyEntry(
            data,
            BodyEntry(date = TODAY, weightKg = 79.4, bodyFatPct = 23.5,
                measurementsCm = mapOf(MeasurementSites.ARM_LEFT to 37.5)),
        )

        val store = store()
        store.save(data)
        val loaded = store.load()!!

        assertEquals(data.sessions.size, loaded.sessions.size)
        assertEquals(17.5, loaded.sessions.single().exercises.single().sets.single().weightKg, 0.0)
        assertEquals(TODAY, loaded.sessions.single().date)
        assertEquals(79.4, loaded.latestWeightKg!!, 0.0)
        assertEquals(37.5, loaded.body.last().measurementsCm[MeasurementSites.ARM_LEFT]!!, 0.0)
        assertEquals(data.exercises.size, loaded.exercises.size)
        assertEquals(data.programmes.single().routines.size, loaded.programmes.single().routines.size)
    }

    @Test
    fun `an in-progress session survives being killed and reopened`() {
        val data = Actions.startSession(baseData(), "routine-push", TODAY, 5_000L)
        val store = store()
        store.save(data)

        val loaded = store.load()!!
        assertNotNull(loaded.activeSession)
        assertEquals("Heavy Push + Quads", loaded.activeSession!!.title)
        assertEquals(6, loaded.activeSession!!.exercises.size)
    }

    @Test
    fun `nothing saved yet loads as nothing rather than failing`() {
        assertNull(store().load())
    }

    @Test
    fun `a corrupted file falls back to the last good backup`() {
        val file = File(folder.root, "winter-arc.json")
        val store = FileStore(file)
        val good = baseData()
        store.save(good)
        store.save(good.copy(prefs = Prefs(unit = WeightUnit.LB)))   // creates the backup

        file.writeText("{ this is not json")

        val loaded = store.load()
        assertNotNull("Expected recovery from the backup", loaded)
        assertEquals(good.exercises.size, loaded!!.exercises.size)
    }

    @Test
    fun `an unknown field written by a newer build does not discard history`() {
        val json = DataCodec.encode(baseData())
        val withExtra = json.replaceFirst("{", """{ "somethingAddedLater": true,""")
        val decoded = DataCodec.decode(withExtra)
        assertEquals(Seed.catalogue.size, decoded.exercises.size)
    }

    @Test
    fun `dates round-trip in a readable ISO form`() {
        val json = DataCodec.encode(baseData())
        assertTrue(json.contains("\"$TODAY\""))
        assertTrue(json.contains("\"2026-09-07\""))       // programme start
        assertTrue(json.contains("\"TUESDAY\""))          // day of week
    }

    @Test
    fun `the CSV export writes one row per set in kilograms`() {
        var data = Actions.startSession(baseData(), "routine-arms", TODAY, 1_000L)
        val ex = data.activeSession!!.exercises.first()
        data = Actions.updateSet(data, ex.id, ex.sets[0].id, weightKg = 30.0, reps = 8, done = true)
        data = Actions.updateSet(data, ex.id, ex.sets[1].id, weightKg = 32.5, reps = 6, done = true)
        data = Actions.finishSession(data, 2_000L)

        val csv = CsvExport.sets(data)
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)                                  // header + two sets
        assertTrue(lines[0].startsWith("date,session,exercise"))
        assertTrue(lines[1].contains("Close-Grip Bench Press"))
        assertTrue(lines[1].contains("Triceps"))
        assertTrue(lines[2].contains("32.50"))
    }

    @Test
    fun `a comma in a name cannot break the CSV`() {
        var data = baseData()
        val id = newId()
        data = Actions.addExercise(data, Exercise(id = id, name = "Row, single arm", muscle = Muscle.BACK))
        data = Actions.startSession(data, null, TODAY, 1_000L)
        data = Actions.addExerciseToSession(data, id, sets = 1)
        val ex = data.activeSession!!.exercises.single()
        data = Actions.updateSet(data, ex.id, ex.sets.single().id, weightKg = 40.0, reps = 10, done = true)
        data = Actions.finishSession(data, 2_000L)

        val row = CsvExport.sets(data).trim().lines()[1]
        assertTrue(row.contains("\"Row, single arm\""))
    }

    @Test
    fun `the body export includes every tracked site`() {
        var data = baseData()
        data = Actions.saveBodyEntry(
            data,
            BodyEntry(date = TODAY.plusDays(1), weightKg = 79.0, bodyFatPct = 23.0,
                measurementsCm = mapOf(MeasurementSites.WAIST to 88.0)),
        )
        val csv = CsvExport.body(data)
        assertTrue(csv.lines()[0].contains("waist_cm"))
        assertTrue(csv.contains("88"))
    }
}

class FmtTest {

    @Test
    fun `whole numbers lose their decimal point but halves do not`() {
        assertEquals("20", Fmt.trim(20.0))
        assertEquals("17.5", Fmt.trim(17.5))
        assertEquals("2.25", Fmt.trim(2.25))
    }

    @Test
    fun `weights convert to pounds only for display`() {
        assertEquals("100", Fmt.weight(100.0, WeightUnit.KG))
        assertEquals("220.46", Fmt.weight(100.0, WeightUnit.LB))
        assertEquals(100.0, Fmt.fromDisplayWeight(Fmt.toDisplayWeight(100.0, WeightUnit.LB), WeightUnit.LB), 0.0001)
    }

    @Test
    fun `large tonnage is abbreviated so it stays readable`() {
        assertEquals("980", Fmt.volume(980.0, WeightUnit.KG))
        assertEquals("12.5k", Fmt.volume(12_500.0, WeightUnit.KG))
        assertEquals("1.2M", Fmt.volume(1_200_000.0, WeightUnit.KG))
    }

    @Test
    fun `the rest clock pads seconds`() {
        assertEquals("2:30", Fmt.clock(150))
        assertEquals("0:05", Fmt.clock(5))
        assertEquals("0:00", Fmt.clock(-10))
    }

    @Test
    fun `durations read in hours and minutes`() {
        assertEquals("45m", Fmt.duration(45))
        assertEquals("1h", Fmt.duration(60))
        assertEquals("1h 30m", Fmt.duration(90))
    }

    @Test
    fun `a gain is signed and a loss is not double signed`() {
        assertEquals("+2.5", Fmt.signed(2.5))
        assertEquals("-2.5", Fmt.signed(-2.5))
    }
}
