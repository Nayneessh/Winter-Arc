package com.winterarc.app

import android.app.Application
import java.io.File

class WinterArcApplication : Application() {

    lateinit var repository: Repository
        private set

    lateinit var autoBackup: AutoBackup
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(
            dataFile = File(filesDir, "winter-arc.json"),
            // Read only when there is no save file yet, so an update never overwrites training
            // that has been logged since the build was cut.
            shippedSeed = {
                assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
            },
        )
        autoBackup = AutoBackup(this)
        repository.onPersisted = { json -> autoBackup.write(json) }

        // Synchronous by design -- see Repository.load().
        repository.load()
    }

    private companion object {
        const val SEED_ASSET = "seed.json"
    }
}
