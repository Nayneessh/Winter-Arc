package com.winterarc.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        ProgrammeEntity::class,
        WorkoutTemplateEntity::class,
        PlannedExerciseEntity::class,
        WorkoutSessionEntity::class,
        PerformedExerciseEntity::class,
        ActualSetEntity::class,
        BodyMetricEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WinterArcDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programmeDao(): ProgrammeDao
    abstract fun templateDao(): TemplateDao
    abstract fun plannedExerciseDao(): PlannedExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun performedExerciseDao(): PerformedExerciseDao
    abstract fun actualSetDao(): ActualSetDao
    abstract fun bodyMetricDao(): BodyMetricDao

    companion object {
        const val NAME = "winter_arc.db"

        fun build(context: Context): WinterArcDatabase =
            Room.databaseBuilder(context.applicationContext, WinterArcDatabase::class.java, NAME)
                // Foreign keys are enforced so an orphaned set can never outlive its exercise.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
