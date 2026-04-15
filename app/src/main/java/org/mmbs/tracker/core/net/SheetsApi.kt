package org.mmbs.tracker.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.mmbs.tracker.core.auth.ServiceAccountAuth
import java.io.IOException
import java.net.URLEncoder

/**
 * Thin wrapper over the Google Sheets API v4 REST surface we actually use.
 *
 * We avoid the official Java client library (~2 MB) by speaking the REST
 * protocol directly. The endpoints we touch are a very small subset:
 *
 *   - values.batchGet      (sync pull)
 *   - values.batchUpdate   (sync push)
 *   - values.append        (new rows)
 *   - values.update        (single-row overwrite)
 *
 * All responses are kotlinx.serialization JsonElements — callers pluck the
 * bits they need rather than deserialising whole schemas.
 */
class SheetsApi(
    private val auth: ServiceAccountAuth,
    private val http: OkHttpClient = HttpModule.client,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Batch-read multiple A1 ranges in one HTTP call.
     * Returns a List<List<List<String>>>: one outer entry per requested range,
     * then rows, then per-cell string values (numbers are passed as their
     * formatted string; nulls are empty string).
     */
    suspend fun batchGet(
        spreadsheetId: String,
        ranges: List<String>,
        valueRenderOption: String = "UNFORMATTED_VALUE",
        dateTimeRenderOption: String = "FORMATTED_STRING",
    ): List<List<List<String>>> = withContext(Dispatchers.IO) {
        if (ranges.isEmpty()) return@withContext emptyList()

        val url = buildString {
            append("https://sheets.googleapis.com/v4/spreadsheets/")
            append(spreadsheetId)
            append("/values:batchGet?")
            ranges.forEach { r ->
                append("ranges=").append(URLEncoder.encode(r, "UTF-8")).append('&')
            }
            append("valueRenderOption=").append(valueRenderOption)
            append("&dateTimeRenderOption=").append(dateTimeRenderOption)
            append("&majorDimension=ROWS")
        }

        val token = auth.accessToken()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("batchGet HTTP ${resp.code}: $text")
            val root = json.parseToJsonElement(text).jsonObject
            val valueRanges = root["valueRanges"]?.jsonArray ?: JsonArray(emptyList())
            valueRanges.map { vr ->
                val values = vr.jsonObject["values"]?.jsonArray
                values?.map { row ->
                    row.jsonArray.map { cell -> cell.toCellString() }
                } ?: emptyList()
            }
        }
    }

    /**
     * Single-call batchUpdate using the USER_ENTERED valueInputOption. Each
     * range+values pair maps to one entry in the "data" array.
     * `values` must be List<List<String>> — one row per inner list.
     */
    suspend fun valuesBatchUpdate(
        spreadsheetId: String,
        updates: List<RangeUpdate>,
    ) = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext
        val body = buildJsonObject {
            put("valueInputOption", JsonPrimitive("USER_ENTERED"))
            put("data", buildJsonArray {
                updates.forEach { u ->
                    add(buildJsonObject {
                        put("range", JsonPrimitive(u.range))
                        put("majorDimension", JsonPrimitive("ROWS"))
                        put("values", buildJsonArray {
                            u.values.forEach { row ->
                                add(buildJsonArray {
                                    row.forEach { cell -> add(JsonPrimitive(cell)) }
                                })
                            }
                        })
                    })
                }
            })
        }
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate"
        val token = auth.accessToken()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("batchUpdate HTTP ${resp.code}: $text")
        }
    }

    /**
     * Append a single row to a sheet. Returns the absolute row index (1-based)
     * that was written, parsed from the response's updatedRange (e.g. "Sheet!A42:X42"
     * → 42).
     */
    suspend fun valuesAppend(
        spreadsheetId: String,
        range: String,
        row: List<String>,
    ): Int = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("values", buildJsonArray {
                add(buildJsonArray { row.forEach { add(JsonPrimitive(it)) } })
            })
        }
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/" +
            URLEncoder.encode(range, "UTF-8") +
            "/:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
        val token = auth.accessToken()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("append HTTP ${resp.code}: $text")
            val root = json.parseToJsonElement(text).jsonObject
            val updated = root["updates"]?.jsonObject?.get("updatedRange")?.jsonPrimitive?.content
                ?: throw IOException("append response missing updatedRange: $text")
            parseFirstRow(updated)
        }
    }

    data class RangeUpdate(val range: String, val values: List<List<String>>)

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** "Sheet1!A42:X42" → 42. Throws on unexpected shape. */
        internal fun parseFirstRow(a1: String): Int {
            val bang = a1.indexOf('!').let { if (it < 0) 0 else it + 1 }
            val cellRef = a1.substring(bang).substringBefore(':')
            return cellRef.dropWhile { it.isLetter() || it == '$' }.toInt()
        }

        /** Convert arbitrary JsonElement cell to string (numbers lose no precision). */
        private fun JsonElement.toCellString(): String = when (this) {
            is JsonNull -> ""
            is JsonPrimitive -> if (isString) content else content
            else -> toString()
        }
    }
}
