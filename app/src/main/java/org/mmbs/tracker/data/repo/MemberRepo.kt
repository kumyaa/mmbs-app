package org.mmbs.tracker.data.repo

import androidx.lifecycle.LiveData
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.dao.MemberDao
import org.mmbs.tracker.data.local.dao.StatusCount
import org.mmbs.tracker.data.local.entity.MemberEntity

class MemberRepo(private val dao: MemberDao) {

    fun observeAll(): LiveData<List<MemberEntity>> = dao.observeAll()
    suspend fun getAll(): List<MemberEntity> = dao.getAll()
    suspend fun get(id: String): MemberEntity? = dao.get(id)
    fun observe(id: String): LiveData<MemberEntity?> = dao.observe(id)
    suspend fun count(): Int = dao.count()
    suspend fun countByStatus(): List<StatusCount> = dao.countByStatus()
    suspend fun dirty(): List<MemberEntity> = dao.getDirty()

    /**
     * Returns the next MM-XXXX id, padded to 4 digits.
     *
     * Safety floor: even if the DB is empty or carries an unexpectedly low
     * max (e.g. fresh install before sync), we never emit an id below
     * MM-0170, because MM-0001..MM-0169 are already taken in the live sheet.
     * This avoids any chance of re-using a historic member number.
     */
    suspend fun nextMemberId(): String {
        val last = dao.lastMemberId()
        val n = last?.removePrefix("MM-")?.toIntOrNull() ?: 0
        return "MM-%04d".format(n.coerceAtLeast(169) + 1)
    }

    suspend fun upsert(entity: MemberEntity) = dao.upsert(entity)
    suspend fun upsertAll(entities: List<MemberEntity>) = dao.upsertAll(entities)
    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Apply a local edit: bumps lastLocalModifiedAt and marks PENDING so the
     * next sync push picks it up.
     */
    suspend fun saveLocalEdit(entity: MemberEntity) {
        dao.upsert(
            entity.copy(
                syncStatus = SyncState.PENDING,
                lastLocalModifiedAt = System.currentTimeMillis(),
                pushError = null,
            )
        )
    }
}
