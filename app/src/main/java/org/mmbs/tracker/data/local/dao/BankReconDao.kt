package org.mmbs.tracker.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.entity.BankReconEntity

@Dao
interface BankReconDao {

    @Query("SELECT * FROM bank_recon ORDER BY reconDate DESC")
    fun observeAll(): LiveData<List<BankReconEntity>>

    @Query("SELECT * FROM bank_recon ORDER BY reconDate DESC")
    suspend fun getAll(): List<BankReconEntity>

    @Query("SELECT * FROM bank_recon WHERE reconId = :id LIMIT 1")
    suspend fun get(id: String): BankReconEntity?

    @Query("SELECT * FROM bank_recon WHERE syncStatus != :state")
    suspend fun getDirty(state: SyncState = SyncState.SYNCED): List<BankReconEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BankReconEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BankReconEntity>)

    @Update
    suspend fun update(entity: BankReconEntity)

    @Query("DELETE FROM bank_recon WHERE reconId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM bank_recon")
    suspend fun clear()
}
