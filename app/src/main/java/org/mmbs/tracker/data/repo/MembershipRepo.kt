package org.mmbs.tracker.data.repo

import androidx.lifecycle.LiveData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.dao.MembershipRowDao
import org.mmbs.tracker.data.local.entity.MembershipRowEntity
import org.mmbs.tracker.domain.model.FyCell
import org.mmbs.tracker.sync.RowMapper

class MembershipRepo(private val dao: MembershipRowDao) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun observeAll(): LiveData<List<MembershipRowEntity>> = dao.observeAll()
    suspend fun getAll(): List<MembershipRowEntity> = dao.getAll()
    suspend fun get(id: String): MembershipRowEntity? = dao.get(id)
    fun observe(id: String): LiveData<MembershipRowEntity?> = dao.observe(id)
    suspend fun dirty(): List<MembershipRowEntity> = dao.getDirty()

    suspend fun upsert(entity: MembershipRowEntity) = dao.upsert(entity)
    suspend fun upsertAll(entities: List<MembershipRowEntity>) = dao.upsertAll(entities)

    /**
     * Record a payment for the given member against the given FY. Creates the
     * membership row if missing.
     */
    suspend fun recordPayment(
        memberId: String,
        primaryName: String,
        fyLabel: String,
        cell: FyCell,
    ) {
        val existing = dao.get(memberId)
        val fees = existing?.let { RowMapper.Membership.parseFees(it.feesJson) }
            ?.toMutableMap()
            ?: mutableMapOf()
        fees[fyLabel] = cell
        val feesJson = json.encodeToString(fees)

        val updated = (existing ?: MembershipRowEntity(
            memberId = memberId,
            primaryName = primaryName,
            feesJson = "",
        )).copy(
            primaryName = primaryName,
            feesJson = feesJson,
            syncStatus = SyncState.PENDING,
            lastLocalModifiedAt = System.currentTimeMillis(),
            pushError = null,
        )
        dao.upsert(updated)
    }
}
