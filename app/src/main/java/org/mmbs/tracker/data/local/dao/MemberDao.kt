package org.mmbs.tracker.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.entity.MemberEntity

@Dao
interface MemberDao {

    @Query("SELECT * FROM members ORDER BY primaryName")
    fun observeAll(): LiveData<List<MemberEntity>>

    @Query("SELECT * FROM members ORDER BY primaryName")
    suspend fun getAll(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE memberId = :id LIMIT 1")
    suspend fun get(id: String): MemberEntity?

    @Query("SELECT * FROM members WHERE memberId = :id LIMIT 1")
    fun observe(id: String): LiveData<MemberEntity?>

    @Query("SELECT * FROM members WHERE syncStatus != :state")
    suspend fun getDirty(state: SyncState = SyncState.SYNCED): List<MemberEntity>

    @Query("SELECT memberId FROM members ORDER BY memberId DESC LIMIT 1")
    suspend fun lastMemberId(): String?

    @Query("SELECT COUNT(*) FROM members")
    suspend fun count(): Int

    @Query("SELECT status, COUNT(*) AS n FROM members GROUP BY status")
    suspend fun countByStatus(): List<StatusCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MemberEntity>)

    @Update
    suspend fun update(entity: MemberEntity)

    @Query("DELETE FROM members WHERE memberId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM members")
    suspend fun clear()
}

data class StatusCount(val status: String, val n: Int)
