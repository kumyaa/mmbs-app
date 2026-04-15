package org.mmbs.tracker.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.entity.TransactionEntity

@Dao
interface TransactionDao {

    /**
     * Observed list ordered by sheetRowIndex so running-balance order is
     * preserved. Rows without a sheetRowIndex (brand-new, not yet pushed)
     * sort last.
     */
    @Query(
        "SELECT * FROM transactions " +
            "ORDER BY CASE WHEN sheetRowIndex IS NULL THEN 1 ELSE 0 END, sheetRowIndex"
    )
    fun observeAll(): LiveData<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions " +
            "ORDER BY CASE WHEN sheetRowIndex IS NULL THEN 1 ELSE 0 END, sheetRowIndex"
    )
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE txnId = :id LIMIT 1")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE txnId = :id LIMIT 1")
    fun observe(id: String): LiveData<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE syncStatus != :state")
    suspend fun getDirty(state: SyncState = SyncState.SYNCED): List<TransactionEntity>

    @Query("SELECT txnId FROM transactions ORDER BY txnId DESC LIMIT 1")
    suspend fun lastTxnId(): String?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * All transactions from a given sheetRowIndex onwards. Used to refresh the
     * running-balance chain after an insert/edit/delete in the middle.
     */
    @Query("SELECT * FROM transactions WHERE sheetRowIndex IS NOT NULL AND sheetRowIndex >= :fromRow ORDER BY sheetRowIndex")
    suspend fun getFromRow(fromRow: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE memberId = :memberId ORDER BY sheetRowIndex")
    suspend fun getByMember(memberId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE txnId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
