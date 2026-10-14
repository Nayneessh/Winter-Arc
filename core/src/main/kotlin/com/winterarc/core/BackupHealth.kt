package com.winterarc.core

/** How a single line of defence is doing. */
enum class ProtectionState { ACTIVE, STALE, FAILING, OFF }

/**
 * One place training is kept.
 *
 * [counts] marks the copies that would still exist if this phone went under a bus. Only those
 * decide the verdict: a copy that lives and dies with the device is worth having -- it is what
 * recovers a bad write -- but it is not what makes training safe.
 */
data class Protection(
    val name: String,
    val state: ProtectionState,
    val detail: String,
    val counts: Boolean,
)

enum class Verdict { AT_RISK, THIN, SAFE }

data class BackupStatus(
    val protections: List<Protection>,
    val verdict: Verdict,
    val offDeviceCopies: Int,
    val headline: String,
    val advice: String?,
)

/**
 * Reads the state of every backup layer and says, in one line, whether the training is actually
 * safe -- then names the single most useful thing left to do.
 *
 * This exists because the failure that cost a month of training was silent. Every layer here can
 * stop working without anything going wrong on screen: a folder permission is revoked, a provider
 * goes offline, a cloud project is paused, an account is signed out. A backup nobody is checking
 * is a backup that will be found missing on the day it is needed, so the checking is done here
 * and shown without being asked for.
 */
object BackupHealth {

    /** Past this many days without a copy, a layer is treated as broken rather than quiet. */
    const val STALE_AFTER_DAYS = 10L

    private const val DAY_MILLIS = 86_400_000L

    fun assess(
        snapshotCount: Int,
        hasLocalBackupFile: Boolean,
        folderConfigured: Boolean,
        folderLastWriteMillis: Long?,
        folderConsecutiveFailures: Int,
        cloudConnected: Boolean,
        cloudLastUploadMillis: Long?,
        nowMillis: Long,
    ): BackupStatus {
        val folder = folderProtection(
            folderConfigured, folderLastWriteMillis, folderConsecutiveFailures, nowMillis,
        )
        val cloud = cloudProtection(cloudConnected, cloudLastUploadMillis, nowMillis)
        val protections = listOf(
            folder,
            cloud,
            systemProtection(),
            onDeviceProtection(snapshotCount, hasLocalBackupFile),
        )

        val offDevice = protections.count { it.counts && it.state == ProtectionState.ACTIVE }
        val verdict = when {
            offDevice == 0 -> Verdict.AT_RISK
            offDevice == 1 -> Verdict.THIN
            else -> Verdict.SAFE
        }

        return BackupStatus(
            protections = protections,
            verdict = verdict,
            offDeviceCopies = offDevice,
            headline = headline(offDevice),
            advice = advice(folder, cloud, cloudConnected, verdict),
        )
    }

    private fun folderProtection(
        configured: Boolean,
        lastWrite: Long?,
        failures: Int,
        now: Long,
    ): Protection {
        val name = "Backup folder"
        return when {
            !configured -> Protection(
                name, ProtectionState.OFF,
                "No folder chosen. Nothing is being written outside the app.",
                counts = false,
            )
            failures >= 2 -> Protection(
                name, ProtectionState.FAILING,
                "The last $failures copies failed. The folder was moved, deleted, or its " +
                    "permission was withdrawn — choose it again.",
                counts = false,
            )
            lastWrite == null -> Protection(
                name, ProtectionState.STALE,
                "Chosen, but no copy has been written yet.",
                counts = false,
            )
            daysSince(lastWrite, now) > STALE_AFTER_DAYS -> Protection(
                name, ProtectionState.STALE,
                "Last copy ${daysSince(lastWrite, now)} days ago. Open the app and leave it " +
                    "again, or write one now.",
                counts = false,
            )
            else -> Protection(
                name, ProtectionState.ACTIVE,
                "Copy written ${agoLabel(daysSince(lastWrite, now))}.",
                counts = true,
            )
        }
    }

    private fun cloudProtection(connected: Boolean, lastUpload: Long?, now: Long): Protection {
        val name = "Cloud backup"
        return when {
            !connected -> Protection(
                name, ProtectionState.OFF,
                "Not connected. Nothing has ever been uploaded.",
                counts = false,
            )
            lastUpload == null -> Protection(
                name, ProtectionState.STALE,
                "Connected, but nothing has been uploaded yet.",
                counts = false,
            )
            daysSince(lastUpload, now) > STALE_AFTER_DAYS -> Protection(
                name, ProtectionState.STALE,
                "Last upload ${daysSince(lastUpload, now)} days ago. The project may have been " +
                    "paused, or the connection expired.",
                counts = false,
            )
            else -> Protection(
                name, ProtectionState.ACTIVE,
                "Uploaded ${agoLabel(daysSince(lastUpload, now))}. Uploads can add a version " +
                    "but can never destroy one.",
                counts = true,
            )
        }
    }

    /**
     * Android's own backup, which carries the save file to the Google account and restores it
     * onto a replacement phone.
     *
     * It is deliberately not counted. The app opts in, but whether it actually runs depends on a
     * system setting an app is not allowed to read -- and a copy that cannot be verified must not
     * be allowed to make the total look better than it is.
     */
    private fun systemProtection(): Protection = Protection(
        name = "Android backup",
        state = ProtectionState.ACTIVE,
        detail = "Switched on for this app: the save file, its spare and every snapshot ride " +
            "to your Google account and come back on a new phone. Not counted here, because " +
            "an app cannot read whether you have Android backup enabled — check Settings › " +
            "Google › Backup.",
        counts = false,
    )

    private fun onDeviceProtection(snapshots: Int, hasBackupFile: Boolean): Protection {
        val pieces = buildList {
            if (hasBackupFile) add("a spare of the last good save")
            if (snapshots > 0) add("$snapshots dated snapshot${if (snapshots == 1) "" else "s"}")
        }
        return if (pieces.isEmpty()) {
            Protection(
                "On this phone", ProtectionState.STALE,
                "No snapshots yet. They appear as you train.",
                counts = false,
            )
        } else {
            Protection(
                "On this phone", ProtectionState.ACTIVE,
                "${pieces.joinToString(" and ")}. These undo a bad write, but they are lost " +
                    "with the phone.",
                counts = false,
            )
        }
    }

    private val numbers =
        listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight")

    private fun spell(n: Int): String = numbers.getOrNull(n) ?: n.toString()

    private fun headline(offDevice: Int): String =
        if (offDevice == 0) "Your training exists in one place."
        else "Your training exists in ${spell(offDevice + 1)} places."

    /** One instruction, not a list -- the most valuable thing left undone. */
    private fun advice(
        folder: Protection,
        cloud: Protection,
        cloudConnected: Boolean,
        verdict: Verdict,
    ): String? = when {
        folder.state == ProtectionState.FAILING ->
            "Fix the backup folder first — it is failing, not merely idle."
        // Connected and quiet is a different problem from never connected: something that was
        // working has stopped, and that is worth interrupting for.
        cloudConnected && cloud.state != ProtectionState.ACTIVE ->
            "The cloud copy has gone quiet. Upload one now to confirm the project is awake."
        verdict == Verdict.AT_RISK ->
            "Choose a backup folder. If this phone is lost today, everything goes with it."
        folder.state != ProtectionState.ACTIVE ->
            "Set up the backup folder — it is the copy that does not depend on anyone's server."
        cloud.state != ProtectionState.ACTIVE ->
            "Connect the cloud backup, so a lost phone is an inconvenience and not a loss."
        else -> null
    }

    private fun daysSince(then: Long, now: Long): Long =
        ((now - then).coerceAtLeast(0L)) / DAY_MILLIS

    private fun agoLabel(days: Long): String = when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }
}
