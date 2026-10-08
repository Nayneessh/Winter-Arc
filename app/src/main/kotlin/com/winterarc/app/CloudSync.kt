package com.winterarc.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One stored backup, as listed from the server. */
data class CloudBackup(
    val id: String,
    val createdAt: String,
    val sessionCount: Int,
    val setCount: Int,
    val volumeKg: Double,
    val deviceLabel: String?,
)

data class SyncResult(val ok: Boolean, val message: String)

/**
 * Backs the training up to the user's own Supabase project.
 *
 * Written against the REST endpoints directly rather than through a client library: the surface
 * used here is four calls, and a hand-rolled HTTP layer has no version to fight and nothing to
 * break under R8 in a release build.
 *
 * The table it writes to is append-only -- no update policy exists on the server -- so an upload
 * can add a version but can never destroy one. That is deliberate. Everything that went wrong
 * with this app's data went wrong because a write replaced something good with something bad; a
 * store where writing cannot overwrite removes that entire class of failure.
 *
 * Credentials are the user's own and are never stored: only the tokens the server returns are
 * kept, in the app's private preferences, and signing out clears them.
 */
class CloudSync(context: Context) {

    private val prefs = context.getSharedPreferences("winter-arc-cloud", Context.MODE_PRIVATE)

    val signedInAccount: String? get() = prefs.getString(KEY_ACCOUNT, null)
    val isSignedIn: Boolean get() = accessToken != null && signedInAccount != null
    val lastUploadLabel: String? get() = prefs.getString(KEY_LAST_UPLOAD, null)

    private val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    private val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)
    private val userId: String? get() = prefs.getString(KEY_USER, null)

    // -- account ---------------------------------------------------------------------------

    fun register(account: String, secret: String): SyncResult {
        val body = JSONObject().put("email", account).put("password", secret)
        val response = post("$ENDPOINT/auth/v1/signup", body, auth = false)
            ?: return SyncResult(false, "Could not reach the server.")

        if (response.has("error_description") || response.has("msg")) {
            val msg = response.optString("error_description").ifBlank { response.optString("msg") }
            return SyncResult(false, msg.ifBlank { "Registration was refused." })
        }
        // A project with confirmation enabled returns the account but no session. That is not a
        // failure -- the account simply is not usable until the emailed link is opened.
        return if (response.optString("access_token", "").isBlank()) {
            SyncResult(false, "Account created. Open the confirmation link sent to you, then connect.")
        } else {
            storeSession(response, account)
            SyncResult(true, "Connected as $account.")
        }
    }

    fun connect(account: String, secret: String): SyncResult {
        val body = JSONObject().put("email", account).put("password", secret)
        val response = post("$ENDPOINT/auth/v1/token?grant_type=password", body, auth = false)
            ?: return SyncResult(false, "Could not reach the server.")

        if (response.optString("access_token", "").isBlank()) {
            val msg = response.optString("error_description").ifBlank { response.optString("msg") }
            return SyncResult(false, msg.ifBlank { "Those details were not accepted." })
        }
        storeSession(response, account)
        return SyncResult(true, "Connected as $account.")
    }

    fun disconnect() = prefs.edit().clear().apply()

    private fun storeSession(response: JSONObject, account: String) {
        prefs.edit()
            .putString(KEY_ACCESS, response.optString("access_token"))
            .putString(KEY_REFRESH, response.optString("refresh_token"))
            .putString(KEY_ACCOUNT, account)
            .putString(KEY_USER, response.optJSONObject("user")?.optString("id"))
            .apply()
    }

    /** Access tokens expire within the hour; the refresh token is what makes sync unattended. */
    private fun renew(): Boolean {
        val token = refreshToken ?: return false
        val response = post(
            "$ENDPOINT/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", token),
            auth = false,
        ) ?: return false
        if (response.optString("access_token", "").isBlank()) return false
        prefs.edit()
            .putString(KEY_ACCESS, response.optString("access_token"))
            .putString(KEY_REFRESH, response.optString("refresh_token"))
            .putString(KEY_USER, response.optJSONObject("user")?.optString("id") ?: userId)
            .apply()
        return true
    }

    // -- backups ---------------------------------------------------------------------------

    fun upload(
        json: String,
        sessionCount: Int,
        setCount: Int,
        volumeKg: Double,
        appVersion: String,
        deviceLabel: String,
        allowRetry: Boolean = true,
    ): SyncResult {
        val uid = userId?.takeIf { isSignedIn } ?: return SyncResult(false, "Not connected.")

        val row = JSONObject()
            .put("user_id", uid)
            .put("session_count", sessionCount)
            .put("set_count", setCount)
            .put("volume_kg", volumeKg)
            .put("app_version", appVersion)
            .put("device_label", deviceLabel)
            .put("payload_hash", sha256(json))
            .put("payload", JSONObject(json))

        return when (val code = postForCode("$ENDPOINT/rest/v1/training_backups", row)) {
            in 200..299 -> {
                markUploaded()
                SyncResult(true, "Backed up $sessionCount sessions.")
            }
            // The unique index on (user, hash) rejects an upload identical to one already stored.
            // Nothing changed, so nothing needed saving: success, not an error.
            409 -> {
                markUploaded()
                SyncResult(true, "Already backed up — nothing has changed.")
            }
            401 -> if (allowRetry && renew()) {
                upload(json, sessionCount, setCount, volumeKg, appVersion, deviceLabel, false)
            } else {
                SyncResult(false, "Connection expired. Connect again.")
            }
            -1 -> SyncResult(false, "No connection.")
            else -> SyncResult(false, "Upload failed ($code).")
        }
    }

    private fun markUploaded() {
        prefs.edit().putString(KEY_LAST_UPLOAD, nowLabel()).apply()
    }

    /** Stored versions, fullest first — the order that matters when recovering. */
    fun list(): List<CloudBackup> {
        if (!isSignedIn) return emptyList()
        val text = get(
            "$ENDPOINT/rest/v1/training_backups" +
                "?select=id,created_at,session_count,set_count,volume_kg,device_label" +
                "&order=session_count.desc,created_at.desc&limit=50",
        ) ?: return emptyList()

        return try {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                CloudBackup(
                    id = o.getString("id"),
                    createdAt = o.optString("created_at").take(16).replace('T', ' '),
                    sessionCount = o.optInt("session_count"),
                    setCount = o.optInt("set_count"),
                    volumeKg = o.optDouble("volume_kg", 0.0),
                    deviceLabel = o.optString("device_label").takeIf { it.isNotBlank() },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Returns the stored document, ready to hand to the importer. */
    fun download(id: String): String? {
        if (!isSignedIn) return null
        val text = get("$ENDPOINT/rest/v1/training_backups?select=payload&id=eq.$id") ?: return null
        return try {
            JSONArray(text).optJSONObject(0)?.optJSONObject("payload")?.toString()
        } catch (_: Exception) {
            null
        }
    }

    // -- plumbing --------------------------------------------------------------------------

    private fun post(url: String, body: JSONObject, auth: Boolean): JSONObject? = try {
        val connection = open(url, "POST", auth)
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val text = stream?.bufferedReader()?.use { it.readText() }
        connection.disconnect()
        text?.let { JSONObject(it) }
    } catch (_: Exception) {
        null
    }

    private fun postForCode(url: String, body: JSONObject): Int = try {
        val connection = open(url, "POST", auth = true)
        connection.setRequestProperty("Prefer", "return=minimal")
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = connection.responseCode
        connection.disconnect()
        code
    } catch (_: Exception) {
        -1
    }

    private fun get(url: String, allowRetry: Boolean = true): String? = try {
        val connection = open(url, "GET", auth = true)
        val code = connection.responseCode
        if (code in 200..299) {
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            text
        } else {
            connection.disconnect()
            if (code == 401 && allowRetry && renew()) get(url, allowRetry = false) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun open(url: String, method: String, auth: Boolean): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            if (auth) accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (method == "POST") doOutput = true
        }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun nowLabel(): String =
        java.time.LocalDateTime.now().toString().take(16).replace('T', ' ')

    private companion object {
        const val ENDPOINT = "https://bwnruvqapzzygmhtgejb.supabase.co"

        // The publishable key. It is designed to ship inside client apps and grants nothing on
        // its own: row-level security decides every read and write, and every policy on the
        // backup table is scoped to the connected account.
        const val PUBLISHABLE_KEY = "sb_publishable_j7PVOkY1eDEBe3tosdZdBQ_ismVr9kU"

        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_ACCOUNT = "account"
        const val KEY_USER = "user"
        const val KEY_LAST_UPLOAD = "last_upload"
    }
}
