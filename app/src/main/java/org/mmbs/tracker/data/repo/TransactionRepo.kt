package org.mmbs.tracker.data.repo

import androidx.lifecycle.LiveData
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.dao.TransactionDao
import org.mmbs.tracker.data.local.entity.TransactionEntity

class TransactionRepo(private val dao: TransactionDao) {

    fun observeAll(): LiveData<List<TransactionEntity>> = dao.observeAll()
    suspend fun getAll(): List<TransactionEntity> = dao.getAll()
    suspend fun get(id: String): TransactionEntity? = dao.get(id)
    fun observe(id: String): LiveData<TransactionEntity?> = dao.observe(id)
    suspend fun count(): Int = dao.count()
    suspend fun dirty(): List<TransactionEntity> = dao.getDirty()
    suspend fun getFromRow(fromRow: Int): List<TransactionEntity> = dao.getFromRow(fromRow)
    suspend fun getByMember(memberId: String): List<TransactionEntity> = dao.getByMember(memberId)

    /** Returns the next TXN-XXXX id, 4-digit padded. */
    suspend fun nextTxnId(): String {
        val last = dao.lastTxnId()
        val n = last?.removePrefix("TXN-")?.toIntOrNull() ?: 0
        return "TXN-%04d".format(n + 1)
    }

    suspend fun upsert(entity: TransactionEntity) = dao.upsert(entity)
    suspend fun upsertAll(entities: List<TransactionEntity>) = dao.upsertAll(entities)
    suspend fun delete(id: String) = dao.delete(id)

    suspend fun saveLocalEdit(entity: TransactionEntity) {
        dao.upsert(
            entity.copy(
                syncStatus = SyncState.PENDING,
                lastLocalModifiedAt = System.currentTimeMillis(),
                pushError = null,
            )
        )
    }
}
