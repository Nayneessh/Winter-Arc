package com.winterarc.app

import android.app.Application
import android.os.Build
import com.winterarc.core.AppData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class WinterArcApplication : Application() {

    lateinit var repository: Repository
        private set

    lateinit var autoBackup: AutoBackup
        private set

    lateinit var cloud: CloudSync
        private set

    /** Outlives any one screen, so an upload is not cancelled by the activity going away. */
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        cloud = CloudSync(this)

        repository.onPersisted = { state, json ->
            // The folder copy is a local file write: fast, and worth finishing before the app is
            // allowed to go away.
            autoBackup.write(json)

            // The upload is not. It is launched rather than awaited so that leaving the app never
            // waits on a network round trip -- and if it does not finish, the next one will.
            uploadScope.launch { uploadQuietly(state, json) }
        }

        // Synchronous by design -- see Repository.load().
        repository.load()
    }

    private fun uploadQuietly(state: AppData, json: String) {
        if (!cloud.isSignedIn) return
        runCatching {
            cloud.upload(
                json = json,
                sessionCount = state.finishedSessionCount,
                setCount = state.sessions.sumOf { it.workingSetCount },
                volumeKg = state.sessions.sumOf { it.volumeKg },
                appVersion = BuildConfig.VERSION_NAME,
                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            )
        }
    }

    private companion object {
        const val SEED_ASSET = "seed.json"
    }
}
