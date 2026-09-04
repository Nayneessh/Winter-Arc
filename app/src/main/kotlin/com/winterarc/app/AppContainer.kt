package com.winterarc.app

import android.content.Context
import com.winterarc.app.data.db.WinterArcDatabase
import com.winterarc.app.data.prefs.SettingsStore
import com.winterarc.app.data.repo.DatabaseSeeder
import com.winterarc.app.data.repo.TrainingRepository
import com.winterarc.app.data.sync.SupabaseClient
import com.winterarc.app.data.sync.SyncEngine
import com.winterarc.domain.programme.Clock
import com.winterarc.domain.programme.IdGenerator
import com.winterarc.domain.programme.WorkoutEngine
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Manual dependency container.
 *
 * A DI framework was considered and rejected. Hilt would add an annotation processor and a
 * compile-time graph to an application with one user, one database and eight screens — cost
 * without a matching benefit, and one more component that can fail a release build. Wiring
 * is explicit here and takes twenty lines.
 */
class AppContainer(context: Context) {

    val database: WinterArcDatabase by lazy { WinterArcDatabase.build(context) }

    val repository: TrainingRepository by lazy { TrainingRepository(database) }

    val settings: SettingsStore by lazy { SettingsStore(context) }

    val seeder: DatabaseSeeder by lazy { DatabaseSeeder(repository) }

    val supabase: SupabaseClient by lazy {
        SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    }

    val syncEngine: SyncEngine by lazy { SyncEngine(context, database, supabase) }

    val clock: Clock = object : Clock {
        override fun now(): Instant = Instant.now()
        override fun today(): LocalDate = LocalDate.now()
    }

    val ids: IdGenerator = IdGenerator { UUID.randomUUID().toString() }

    val workoutEngine: WorkoutEngine by lazy { WorkoutEngine(clock, ids) }
}
