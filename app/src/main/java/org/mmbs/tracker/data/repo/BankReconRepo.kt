package org.mmbs.tracker.data.repo

import androidx.lifecycle.LiveData
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.dao.BankReconDao
import org.mmbs.tracker.data.local.entity.BankReconEntity

/** Bank reconciliation rows. Phase C writes to this; Phase A only syncs. */
class BankReconRepo(private val dao: BankReconDao) {

    fun observeAll(): LiveData<List<BankReconEntity>> = dao.observeAll()
    suspend fun getAll(): List<BankReconEntity> = dao.getAll()
    suspend fun get(id: String): BankReconEntity? = dao.get(id)
    suspend fun dirty(): List<BankReconEntity> = dao.getDirty()

    suspend fun upsert(entity: BankReconEntity) = dao.upsert(entity)
    suspend fun upsertAll(entities: List<BankReconEntity>) = dao.upsertAll(entities)

    suspend fun saveLocalEdit(entity: BankReconEntity) {
        dao.upsert(
            entity.copy(
                syncStatus = SyncState.PENDING,
                lastLocalModifiedAt = System.currentTimeMillis(),
                pushError = null,
            )
        )
    }
}
