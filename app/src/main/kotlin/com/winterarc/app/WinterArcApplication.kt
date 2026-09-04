package com.winterarc.app

import android.app.Application
import java.io.File

class WinterArcApplication : Application() {

    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(File(filesDir, "winter-arc.json"))
        repository.load()
    }
}
