package com.winterarc.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WinterArcApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Seeding is idempotent and guarded; running it off the main thread keeps startup fast.
        scope.launch { runCatching { container.seeder.seedIfEmpty() } }
    }
}
