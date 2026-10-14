package com.winterarc.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.time.LocalDate

/**
 * Keeps a copy of everything in a folder the user chooses, outside the app.
 *
 * The app stores its training in private storage, which is wiped when the app is uninstalled and
 * is reachable by nothing else -- so when the save file was destroyed there was simply nowhere
 * else to look. One file on one device was never a safe place for a year of training, and a
 * backup that has to be remembered is a backup that will not exist on the day it is needed.
 *
 * Once a folder is granted, a copy is written every time the app goes to the background. The
 * permission is persisted across reboots, the folder is ordinary storage the user can see in
 * Files, and it survives the app being uninstalled entirely.
 */
class AutoBackup(private val context: Context) {

    private val prefs = context.getSharedPreferences("winter-arc-backup", Context.MODE_PRIVATE)

    val folderUri: Uri?
        get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)

    /** A readable name for the chosen folder, for showing in settings. */
    val folderLabel: String?
        get() = folderUri?.let { uri ->
            runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                ?: uri.lastPathSegment
        }

    val lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST, 0L)

    /** Null until a copy has actually been written, which the health readout needs to know. */
    val lastBackupAtMillis: Long?
        get() = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0L }

    val lastBackupLabel: String?
        get() = prefs.getString(KEY_LAST_LABEL, null)

    /**
     * How many attempts in a row have failed.
     *
     * A folder backup does not announce its own death: the permission is withdrawn, the provider
     * goes offline, the folder is deleted, and writing simply stops working while the app carries
     * on looking fine. Counting the failures is what turns that into something that can be shown.
     */
    val consecutiveFailures: Int
        get() = prefs.getInt(KEY_FAILURES, 0)

    fun setFolder(uri: Uri) {
        // Without taking the permission persistably the grant dies with the process, and the
        // automatic backup quietly stops working at exactly the point nobody is checking.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
    }

    fun clearFolder() {
        prefs.edit()
            .remove(KEY_FOLDER).remove(KEY_LAST).remove(KEY_LAST_LABEL).remove(KEY_FAILURES)
            .apply()
    }

    /**
     * Writes two files: one always-current copy, and one stamped with today's date.
     *
     * The current copy is what gets restored. The dated ones are the history -- a bad write
     * noticed a week later is still recoverable, which a single overwritten file never is.
     */
    fun write(json: String): Boolean {
        if (folderUri == null) return false
        val ok = runCatching {
            val dir = DocumentFile.fromTreeUri(context, folderUri!!)
            if (dir == null || !dir.canWrite()) {
                false
            } else {
                writeFile(dir, "winter-arc-latest.json", json) &&
                    writeFile(dir, "winter-arc-${LocalDate.now()}.json", json)
            }
        }.getOrDefault(false)

        prefs.edit().apply {
            if (ok) {
                putLong(KEY_LAST, System.currentTimeMillis())
                putString(KEY_LAST_LABEL, LocalDate.now().toString())
                putInt(KEY_FAILURES, 0)
            } else {
                putInt(KEY_FAILURES, consecutiveFailures + 1)
            }
        }.apply()
        return ok
    }

    private fun writeFile(dir: DocumentFile, name: String, json: String): Boolean {
        // Storage providers may append their own extension, so an existing file is matched on the
        // stem rather than the exact name -- otherwise every save creates "name (1).json" again.
        val stem = name.removeSuffix(".json")
        val existing = dir.listFiles().firstOrNull {
            it.isFile && (it.name == name || it.name?.removeSuffix(".json") == stem)
        }
        val doc = existing ?: dir.createFile("application/json", name) ?: return false
        val payload = json.toByteArray()

        val wrote = context.contentResolver.openOutputStream(doc.uri, "wt")?.use {
            it.write(payload)
            it.flush()
            true
        } ?: false
        if (!wrote) return false

        // Read it straight back. A storage provider can accept a write and keep nothing -- a
        // cloud folder with no connection is the ordinary way this happens -- and a copy that
        // was never really written is worse than no copy at all, because it gets counted as
        // protection. Nothing is reported as backed up here until it has been read back.
        return storedSize(doc.uri) == payload.size
    }

    private fun storedSize(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().size } ?: -1
    }.getOrDefault(-1)

    private companion object {
        const val KEY_FOLDER = "folder"
        const val KEY_LAST = "last"
        const val KEY_LAST_LABEL = "last_label"
        const val KEY_FAILURES = "failures"
    }
}
