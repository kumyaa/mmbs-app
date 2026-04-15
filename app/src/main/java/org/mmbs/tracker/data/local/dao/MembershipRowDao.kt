package org.mmbs.tracker.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.entity.MembershipRowEntity

@Dao
interface MembershipRowDao {

    @Query("SELECT * FROM membership_rows ORDER BY primaryName")
    fun observeAll(): LiveData<List<MembershipRowEntity>>

    @Query("SELECT * FROM membership_rows ORDER BY primaryName")
    suspend fun getAll(): List<MembershipRowEntity>

    @Query("SELECT * FROM membership_rows WHERE memberId = :id LIMIT 1")
    suspend fun get(id: String): MembershipRowEntity?

    @Query("SELECT * FROM membership_rows WHERE memberId = :id LIMIT 1")
    fun observe(id: String): LiveData<MembershipRowEntity?>

    @Query("SELECT * FROM membership_rows WHERE syncStatus != :state")
    suspend fun getDirty(state: SyncState = SyncState.SYNCED): List<MembershipRowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MembershipRowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MembershipRowEntity>)

    @Update
    suspend fun update(entity: MembershipRowEntity)

    @Query("DELETE FROM membership_rows WHERE memberId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM membership_rows")
    suspend fun clear()
}
