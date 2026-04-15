package org.mmbs.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.mmbs.tracker.data.local.entity.AppUserEntity

/**
 * AppUsers sheet is read-only from the app; the committee maintains the list
 * directly in the sheet. We only cache it locally for offline role lookup.
 */
@Dao
interface AppUserDao {

    @Query("SELECT * FROM app_users")
    suspend fun getAll(): List<AppUserEntity>

    @Query("SELECT * FROM app_users WHERE email = :email LIMIT 1")
    suspend fun get(email: String): AppUserEntity?

    @Query("SELECT role FROM app_users WHERE email = :email LIMIT 1")
    suspend fun getRole(email: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppUserEntity>)

    @Query("DELETE FROM app_users")
    suspend fun clear()
}
