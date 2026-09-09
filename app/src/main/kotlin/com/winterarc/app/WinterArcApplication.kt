package com.winterarc.app

import android.app.Application
import java.io.File

class WinterArcApplication : Application() {

    lateinit var repository: Repository
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
        repository.load()
    }

    private companion object {
        const val SEED_ASSET = "seed.json"
    }
}
