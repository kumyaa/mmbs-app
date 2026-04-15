package org.mmbs.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.mmbs.tracker.data.local.entity.ConfigKvEntity

/**
 * Config sheet is read-only from the app, so no dirty/push paths are needed.
 */
@Dao
interface ConfigKvDao {

    @Query("SELECT * FROM config_kv")
    suspend fun getAll(): List<ConfigKvEntity>

    @Query("SELECT value FROM config_kv WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConfigKvEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ConfigKvEntity>)

    @Query("DELETE FROM config_kv")
    suspend fun clear()
}
