package org.mmbs.tracker.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mmbs.tracker.core.auth.ServiceAccountAuth
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Subset of the Drive API v3 we need: validate the spreadsheet ID exists and
 * fetch the file's `modifiedTime` for per-sync conflict-detection anchoring.
 */
class DriveApi(
    private val auth: ServiceAccountAuth,
    private val http: OkHttpClient = HttpModule.client,
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class FileMetadata(
        val id: String,
        val name: String,
        val mimeType: String,
        val modifiedTimeEpochMs: Long,
    )

    /** Validates the spreadsheet ID and returns its metadata. */
    suspend fun getSpreadsheetMetadata(spreadsheetId: String): FileMetadata = withContext(Dispatchers.IO) {
        val fields = URLEncoder.encode("id,name,mimeType,modifiedTime", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files/$spreadsheetId?fields=$fields"
        val token = auth.accessToken()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("drive.files.get HTTP ${resp.code}: $text")
            val obj = json.parseToJsonElement(text).jsonObject
            FileMetadata(
                id = obj["id"]?.jsonPrimitive?.content ?: spreadsheetId,
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                mimeType = obj["mimeType"]?.jsonPrimitive?.content ?: "",
                modifiedTimeEpochMs = parseIso8601(obj["modifiedTime"]?.jsonPrimitive?.content),
            )
        }
    }

    companion object {
        internal fun parseIso8601(text: String?): Long {
            if (text.isNullOrBlank()) return 0L
            // Drive returns RFC3339, e.g. 2026-04-14T08:42:17.123Z
            val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
            for (f in formats) {
                try {
                    val sdf = SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    return sdf.parse(text)?.time ?: 0L
                } catch (_: Exception) { /* try next */ }
            }
            return 0L
        }
    }
}
