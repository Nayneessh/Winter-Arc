package com.winterarc.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A minimal Supabase client over PostgREST and GoTrue.
 *
 * Written against HttpURLConnection rather than pulling in an HTTP stack, because the sync
 * surface is small (auth, upsert, select-since) and every additional dependency is another
 * thing that can break a release build for a feature that is strictly optional.
 *
 * NOTHING here is required for the app to work. Every method returns a [SyncResult] and a
 * failure is reported, never thrown into the UI.
 */
class SupabaseClient(
    private val baseUrl: String,
    private val anonKey: String,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    sealed interface SyncResult<out T> {
        data class Ok<T>(val value: T) : SyncResult<T>
        data class Err(val message: String, val code: Int? = null) : SyncResult<Nothing>
    }

    data class Session(val accessToken: String, val refreshToken: String, val userId: String)

    // -----------------------------------------------------------------------
    // Auth
    // -----------------------------------------------------------------------

    suspend fun signUp(email: String, password: String): SyncResult<Session> =
        auth("signup", email, password)

    suspend fun signIn(email: String, password: String): SyncResult<Session> =
        auth("token?grant_type=password", email, password)

    suspend fun refresh(refreshToken: String): SyncResult<Session> = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("refresh_token", JsonPrimitive(refreshToken)) }
        request(
            url = "$baseUrl/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = body.toString(),
            token = null,
        ).mapCatchingSession()
    }

    private suspend fun auth(path: String, email: String, password: String): SyncResult<Session> =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("email", JsonPrimitive(email))
                put("password", JsonPrimitive(password))
            }
            request("$baseUrl/auth/v1/$path", "POST", body.toString(), token = null)
                .mapCatchingSession()
        }

    private fun SyncResult<String>.mapCatchingSession(): SyncResult<Session> = when (this) {
        is SyncResult.Err -> this
        is SyncResult.Ok -> runCatching {
            val obj = json.parseToJsonElement(value).jsonObject
            val access = obj["access_token"]?.jsonPrimitive?.content
            val refresh = obj["refresh_token"]?.jsonPrimitive?.content
            val userId = obj["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content
            if (access == null || refresh == null || userId == null) {
                SyncResult.Err("Sign-in response was missing a token.")
            } else {
                SyncResult.Ok(Session(access, refresh, userId))
            }
        }.getOrElse { SyncResult.Err("Could not read the sign-in response: ${it.message}") }
    }

    // -----------------------------------------------------------------------
    // Data
    // -----------------------------------------------------------------------

    /**
     * Upserts rows into [table]. Uses `resolution=merge-duplicates` so re-pushing a row that
     * already exists updates it instead of failing on the primary key — which is exactly what
     * happens when a push succeeds server-side but the response is lost on a flaky connection.
     */
    suspend fun upsert(table: String, rows: List<JsonObject>, token: String): SyncResult<Int> =
        withContext(Dispatchers.IO) {
            if (rows.isEmpty()) return@withContext SyncResult.Ok(0)
            val payload = JsonArray(rows).toString()
            when (
                val r = request(
                    url = "$baseUrl/rest/v1/$table?on_conflict=id",
                    method = "POST",
                    body = payload,
                    token = token,
                    extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
                )
            ) {
                is SyncResult.Ok -> SyncResult.Ok(rows.size)
                is SyncResult.Err -> r
            }
        }

    /** Rows changed on the server since [sinceIso], for pull-down reconciliation. */
    suspend fun selectSince(table: String, sinceIso: String, token: String): SyncResult<List<JsonObject>> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode("gt.$sinceIso", "UTF-8")
            when (
                val r = request(
                    url = "$baseUrl/rest/v1/$table?updated_at=$q&select=*",
                    method = "GET",
                    body = null,
                    token = token,
                )
            ) {
                is SyncResult.Err -> r
                is SyncResult.Ok -> runCatching {
                    val arr = json.parseToJsonElement(r.value)
                    SyncResult.Ok((arr as? JsonArray)?.map { it.jsonObject } ?: emptyList())
                }.getOrElse { SyncResult.Err("Could not read rows from $table: ${it.message}") }
            }
        }

    // -----------------------------------------------------------------------

    private fun request(
        url: String,
        method: String,
        body: String?,
        token: String?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): SyncResult<String> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
                setRequestProperty("Content-Type", "application/json")
                extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code in 200..299) {
                SyncResult.Ok(text)
            } else {
                SyncResult.Err(readErrorMessage(text, code), code)
            }
        } catch (e: Exception) {
            // Offline is the normal case, not an exceptional one.
            SyncResult.Err(e.message ?: "Network unavailable")
        } finally {
            conn?.disconnect()
        }
    }

    private fun readErrorMessage(text: String, code: Int): String = runCatching {
        val obj = json.parseToJsonElement(text).jsonObject
        obj["message"]?.jsonPrimitive?.content
            ?: obj["error_description"]?.jsonPrimitive?.content
            ?: obj["msg"]?.jsonPrimitive?.content
            ?: "Request failed ($code)"
    }.getOrElse { if (text.isBlank()) "Request failed ($code)" else text.take(200) }
}
