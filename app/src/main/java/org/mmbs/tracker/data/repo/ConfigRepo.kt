package org.mmbs.tracker.data.repo

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.mmbs.tracker.data.local.dao.ConfigKvDao
import org.mmbs.tracker.data.local.entity.ConfigKvEntity

class ConfigRepo(private val dao: ConfigKvDao) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAll(): List<ConfigKvEntity> = dao.getAll()

    suspend fun value(key: String): String? = dao.get(key)

    /** Returns the cached FY label from Config!C4 if present. */
    suspend fun currentFyFromSheet(): String? = dao.get("CELL:C2")?.takeIf { it.isNotBlank() }

    /** Returns the list stored against a label header, or empty. */
    suspend fun listByLabel(label: String): List<String> {
        val raw = dao.get("LABEL:$label") ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun upsertAll(entities: List<ConfigKvEntity>) = dao.upsertAll(entities)
    suspend fun clear() = dao.clear()
}
