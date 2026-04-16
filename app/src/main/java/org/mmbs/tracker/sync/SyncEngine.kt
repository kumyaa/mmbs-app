package org.mmbs.tracker.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mmbs.tracker.core.net.DriveApi
import org.mmbs.tracker.core.net.SheetsApi
import org.mmbs.tracker.core.util.ColumnLetters
import org.mmbs.tracker.core.util.Prefs
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.entity.AppUserEntity
import org.mmbs.tracker.data.local.entity.BankReconEntity
import org.mmbs.tracker.data.local.entity.MemberEntity
import org.mmbs.tracker.data.local.entity.MembershipRowEntity
import org.mmbs.tracker.data.local.entity.TransactionEntity
import org.mmbs.tracker.data.repo.AppUsersRepo
import org.mmbs.tracker.data.repo.BankReconRepo
import org.mmbs.tracker.data.repo.ConfigRepo
import org.mmbs.tracker.data.repo.MemberRepo
import org.mmbs.tracker.data.repo.MembershipRepo
import org.mmbs.tracker.data.repo.TransactionRepo
import org.mmbs.tracker.domain.fy.FinancialYear

/**
 * Bidirectional sync between the local Room store and the Google Sheet.
 *
 * Flow:
 *   1. Drive: get modifiedTime (for the stale banner + cache timestamp).
 *   2. Sheets: batchGet every range we care about in a single HTTP call.
 *   3. For each table: apply conflict detection per row; upsert clean/remote-only
 *      rows; push local-only rows; collect both-sided conflicts for the UI.
 *   4. Push: one batchUpdate with all pending rows (post-conflict-filter).
 *   5. Refresh Drive modifiedTime and persist last-sync timestamp.
 *
 * Phase A only pushes Members, Membership Tracker, Transactions, Bank Recon
 * (which never has local edits in Phase A — placeholder). Config and AppUsers
 * are pull-only.
 */
class SyncEngine(
    private val sheets: SheetsApi,
    private val drive: DriveApi,
    private val prefs: Prefs,
    private val memberRepo: MemberRepo,
    private val membershipRepo: MembershipRepo,
    private val txnRepo: TransactionRepo,
    private val reconRepo: BankReconRepo,
    private val configRepo: ConfigRepo,
    private val appUsersRepo: AppUsersRepo,
) {
    private val mutex = Mutex()

    /**
     * Run one full sync cycle. Returns a SyncResult — inspect hasConflicts
     * before treating the run as complete.
     */
    suspend fun syncNow(): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val sheetId = prefs.spreadsheetId
                ?: return@withContext SyncResult(errors = listOf("No spreadsheet configured"))

            val errors = mutableListOf<String>()
            val conflicts = mutableListOf<ConflictRow>()
            var pulled = 0
            var pushed = 0
            var sheetModifiedAt = 0L

            try {
                sheetModifiedAt = drive.getSpreadsheetMetadata(sheetId).modifiedTimeEpochMs
            } catch (t: Throwable) {
                errors += "Drive metadata: ${t.message}"
            }

            val ranges = listOf(
                SheetSpec.MEMBERS_RANGE,
                SheetSpec.MEMBERSHIP_HEADER_RANGE,
                SheetSpec.MEMBERSHIP_DATA_RANGE,
                SheetSpec.TRANSACTIONS_RANGE,
                SheetSpec.BANK_RECON_RANGE,
                SheetSpec.CONFIG_RANGE,
                SheetSpec.APP_USERS_RANGE,
            )
            val ranged = try {
                sheets.batchGet(sheetId, ranges)
            } catch (t: Throwable) {
                errors += "batchGet: ${t.message}"
                return@withContext SyncResult(errors = errors, tookMs = System.currentTimeMillis() - start)
            }

            // batchGet returns one entry per range, in order.
            val membersRaw      = ranged.getOrNull(0) ?: emptyList()
            val membershipHdr   = ranged.getOrNull(1) ?: emptyList()
            val membershipRows  = ranged.getOrNull(2) ?: emptyList()
            val transactionsRaw = ranged.getOrNull(3) ?: emptyList()
            val bankReconRaw    = ranged.getOrNull(4) ?: emptyList()
            val configRaw       = ranged.getOrNull(5) ?: emptyList()
            val appUsersRaw     = ranged.getOrNull(6) ?: emptyList()

            // --- read-only tables first ---
            try {
                val configEntities = RowMapper.Config.parse(configRaw)
                configRepo.upsertAll(configEntities)
                pulled += configEntities.size
            } catch (t: Throwable) {
                errors += "Config: ${t.message}"
            }

            try {
                val appUserEntities = appUsersRaw
                    .drop(SheetSpec.APP_USERS_DATA_START_ROW - 1)
                    .mapNotNull { RowMapper.AppUsers.fromRow(it) }
                appUsersRepo.clear()
                appUsersRepo.upsertAll(appUserEntities)
                pulled += appUserEntities.size
            } catch (t: Throwable) {
                errors += "AppUsers: ${t.message}"
            }

            // --- Members ---
            val memberConflicts = runCatching {
                syncMembers(membersRaw)
            }.getOrElse {
                errors += "Members: ${it.message}"
                emptyList()
            }
            conflicts += memberConflicts
            pulled += membersRaw.size.coerceAtLeast(0)

            // --- Membership Tracker ---
            val fyColumns = FinancialYear.detectColumns(membershipHdr.firstOrNull().orEmpty())
            // Persist the detected FY labels so Record Payment can populate its
            // dropdown even while offline. Skipped if the header came back empty
            // (network flake / range misread) — we don't want to wipe a good
            // previous list with an empty one.
            if (fyColumns.isNotEmpty()) {
                prefs.knownFyLabels = fyColumns.map { it.label }
            }
            val membershipConflicts = runCatching {
                syncMembership(membershipRows, fyColumns)
            }.getOrElse {
                errors += "Membership: ${it.message}"
                emptyList()
            }
            conflicts += membershipConflicts
            pulled += membershipRows.size

            // --- Transactions ---
            val txnConflicts = runCatching {
                syncTransactions(transactionsRaw)
            }.getOrElse {
                errors += "Transactions: ${it.message}"
                emptyList()
            }
            conflicts += txnConflicts
            pulled += transactionsRaw.size.coerceAtLeast(0)

            // --- Bank Recon (pull only in Phase A) ---
            try {
                val reconEntities = bankReconRaw
                    .drop(SheetSpec.BANK_RECON_DATA_START_ROW - 1)
                    .mapIndexedNotNull { i, row ->
                        val reconId = row.firstOrNull().orEmpty().trim()
                        if (reconId.isBlank()) null
                        else RowMapper.BankRecon.fromRow(
                            row,
                            sheetRowIndex = SheetSpec.BANK_RECON_DATA_START_ROW + i,
                        )
                    }
                reconRepo.upsertAll(reconEntities)
                pulled += reconEntities.size
            } catch (t: Throwable) {
                errors += "BankRecon: ${t.message}"
            }

            // --- Push dirty rows ---
            if (conflicts.isEmpty()) {
                pushed = runCatching { pushAllDirty(sheetId) }.getOrElse {
                    errors += "Push: ${it.message}"
                    0
                }

                // Refresh sheetModifiedAt after a push so the stale banner is fresh.
                if (pushed > 0) {
                    runCatching {
                        sheetModifiedAt = drive.getSpreadsheetMetadata(sheetId).modifiedTimeEpochMs
                    }
                }
            }

            prefs.lastSyncEpochMs = System.currentTimeMillis()
            prefs.sheetModifiedTimeEpochMs = sheetModifiedAt

            SyncResult(
                pulled = pulled,
                pushed = pushed,
                conflicts = conflicts,
                errors = errors,
                sheetModifiedAt = sheetModifiedAt,
                tookMs = System.currentTimeMillis() - start,
            )
        }
    }

    // ---------- Per-table pull/merge ----------

    /** Members: A..X, data starts row 4. */
    private suspend fun syncMembers(raw: List<List<String>>): List<ConflictRow> {
        val dataRows = raw.drop(SheetSpec.MEMBERS_DATA_START_ROW - 1)
        val conflicts = mutableListOf<ConflictRow>()
        val toApply = mutableListOf<MemberEntity>()

        val localById = memberRepo.getAll().associateBy { it.memberId }

        dataRows.forEachIndexed { i, row ->
            val sheetRow = SheetSpec.MEMBERS_DATA_START_ROW + i
            val id = row.firstOrNull().orEmpty().trim()
            if (id.isBlank()) return@forEachIndexed
            val existing = localById[id]

            if (existing == null) {
                // New from sheet.
                toApply += RowMapper.Members.fromRow(row, sheetRow)
                return@forEachIndexed
            }

            val localCells = rowAsMap(RowMapper.Members.toRow(existing), SheetSpec.MEMBERS_COL_COUNT)
            val decision = ConflictDetector.detect(
                snapshotJson = existing.lastSyncedSnapshot,
                remoteRow = row,
                localCells = localCells,
                colCount = SheetSpec.MEMBERS_COL_COUNT,
                isLocalDirty = existing.syncStatus != SyncState.SYNCED,
            )
            when (decision) {
                is RowConflict.Clean -> {
                    // Keep local but refresh snapshot to current remote.
                    toApply += existing.copy(
                        sheetRowIndex = sheetRow,
                        lastSyncedSnapshot = RowMapper.rowToSnapshot(row, SheetSpec.MEMBERS_COL_COUNT),
                    )
                }
                is RowConflict.RemoteOnly -> {
                    toApply += RowMapper.Members.fromRow(row, sheetRow)
                }
                is RowConflict.LocalOnly -> {
                    // Nothing to change at pull time — push pipeline handles it.
                }
                is RowConflict.Both -> {
                    conflicts += ConflictRow(
                        table = "members",
                        rowKey = id,
                        sheetRowIndex = sheetRow,
                        diffs = decision.diffs,
                    )
                }
            }
        }

        if (toApply.isNotEmpty()) memberRepo.upsertAll(toApply)
        return conflicts
    }

    private suspend fun syncMembership(
        raw: List<List<String>>,
        fyColumns: List<FinancialYear.FyColumn>,
    ): List<ConflictRow> {
        val conflicts = mutableListOf<ConflictRow>()
        val toApply = mutableListOf<MembershipRowEntity>()
        val localById = membershipRepo.getAll().associateBy { it.memberId }

        raw.forEachIndexed { i, row ->
            val sheetRow = SheetSpec.MEMBERSHIP_DATA_START_ROW + i
            val id = row.firstOrNull().orEmpty().trim()
            if (id.isBlank()) return@forEachIndexed
            val existing = localById[id]
            val fresh = RowMapper.Membership.fromRow(row, sheetRow, fyColumns)

            if (existing == null) {
                toApply += fresh
                return@forEachIndexed
            }
            val isDirty = existing.syncStatus != SyncState.SYNCED
            if (!isDirty) {
                toApply += fresh
                return@forEachIndexed
            }
            // Dirty locally — compare fees maps.
            val localFees = RowMapper.Membership.parseFees(existing.feesJson)
            val remoteFees = RowMapper.Membership.parseFees(fresh.feesJson)
            val snapshotFees = RowMapper.Membership.parseFees(existing.lastSyncedSnapshot)
            if (remoteFees == snapshotFees) {
                // Local-only change — leave existing for push.
            } else if (localFees == remoteFees) {
                // Converged.
                toApply += fresh
            } else {
                // Both changed — conflict per FY label.
                val diffs = (localFees.keys + remoteFees.keys).sorted().mapNotNull { fy ->
                    val r = remoteFees[fy]?.toString().orEmpty()
                    val l = localFees[fy]?.toString().orEmpty()
                    if (r == l) null
                    else RowConflict.FieldDiff(
                        column = fy,
                        snapshotValue = snapshotFees[fy]?.toString().orEmpty(),
                        remoteValue = r,
                        localValue = l,
                    )
                }
                if (diffs.isNotEmpty()) {
                    conflicts += ConflictRow(
                        table = "membership_rows",
                        rowKey = id,
                        sheetRowIndex = sheetRow,
                        diffs = diffs,
                    )
                }
            }
        }
        if (toApply.isNotEmpty()) membershipRepo.upsertAll(toApply)
        return conflicts
    }

    private suspend fun syncTransactions(raw: List<List<String>>): List<ConflictRow> {
        val dataRows = raw.drop(SheetSpec.TRANSACTIONS_DATA_START_ROW - 1)
        val conflicts = mutableListOf<ConflictRow>()
        val toApply = mutableListOf<TransactionEntity>()
        val localById = txnRepo.getAll().associateBy { it.txnId }

        dataRows.forEachIndexed { i, row ->
            val sheetRow = SheetSpec.TRANSACTIONS_DATA_START_ROW + i
            val id = row.firstOrNull().orEmpty().trim()
            if (id.isBlank()) return@forEachIndexed
            val existing = localById[id]

            if (existing == null) {
                toApply += RowMapper.Transactions.fromRow(row, sheetRow)
                return@forEachIndexed
            }
            val localCells = rowAsMap(RowMapper.Transactions.toRow(existing), SheetSpec.TRANSACTIONS_COL_COUNT)
            val decision = ConflictDetector.detect(
                snapshotJson = existing.lastSyncedSnapshot,
                remoteRow = row,
                localCells = localCells,
                colCount = SheetSpec.TRANSACTIONS_COL_COUNT,
                isLocalDirty = existing.syncStatus != SyncState.SYNCED,
            )
            when (decision) {
                is RowConflict.Clean ->
                    toApply += existing.copy(
                        sheetRowIndex = sheetRow,
                        lastSyncedSnapshot = RowMapper.rowToSnapshot(row, SheetSpec.TRANSACTIONS_COL_COUNT),
                    )
                is RowConflict.RemoteOnly ->
                    toApply += RowMapper.Transactions.fromRow(row, sheetRow)
                is RowConflict.LocalOnly -> Unit
                is RowConflict.Both -> conflicts += ConflictRow(
                    table = "transactions",
                    rowKey = id,
                    sheetRowIndex = sheetRow,
                    diffs = decision.diffs,
                )
            }
        }
        if (toApply.isNotEmpty()) txnRepo.upsertAll(toApply)
        return conflicts
    }

    // ---------- Push ----------

    /**
     * Push every dirty row across all tables in one batchUpdate (Members,
     * Transactions) plus one-by-one appends for newly-created rows without a
     * sheetRowIndex. Membership is pushed as full-row overwrite.
     */
    private suspend fun pushAllDirty(sheetId: String): Int {
        var count = 0

        val memberUpdates = mutableListOf<SheetsApi.RangeUpdate>()
        val memberFreshPayloads = mutableListOf<Pair<MemberEntity, List<String>>>()
        memberRepo.dirty().forEach { m ->
            val row = RowMapper.Members.toRow(m)
            if (m.sheetRowIndex != null) {
                memberUpdates += SheetsApi.RangeUpdate(
                    range = "${SheetSpec.SHEET_MEMBERS}!A${m.sheetRowIndex}:${
                        ColumnLetters.toLetters(SheetSpec.MEMBERS_COL_COUNT - 1)
                    }${m.sheetRowIndex}",
                    values = listOf(row),
                )
                memberFreshPayloads += m to row
            } else {
                // New row — append.
                val newRowIdx = runCatching {
                    sheets.valuesAppend(sheetId, "${SheetSpec.SHEET_MEMBERS}!A:A", row)
                }.getOrElse { t ->
                    memberRepo.upsert(m.copy(syncStatus = SyncState.FAILED, pushError = t.message))
                    return@forEach
                }
                memberRepo.upsert(
                    m.copy(
                        sheetRowIndex = newRowIdx,
                        syncStatus = SyncState.SYNCED,
                        pushError = null,
                        lastSyncedSnapshot = RowMapper.rowToSnapshot(row, SheetSpec.MEMBERS_COL_COUNT),
                    )
                )
                count++
            }
        }

        val txnUpdates = mutableListOf<SheetsApi.RangeUpdate>()
        val txnFreshPayloads = mutableListOf<Pair<TransactionEntity, List<String>>>()
        txnRepo.dirty().forEach { t ->
            val row = RowMapper.Transactions.toRow(t)
            if (t.sheetRowIndex != null) {
                txnUpdates += SheetsApi.RangeUpdate(
                    range = "${SheetSpec.SHEET_TRANSACTIONS}!A${t.sheetRowIndex}:${
                        ColumnLetters.toLetters(SheetSpec.TRANSACTIONS_COL_COUNT - 1)
                    }${t.sheetRowIndex}",
                    values = listOf(row),
                )
                txnFreshPayloads += t to row
            } else {
                val newRowIdx = runCatching {
                    sheets.valuesAppend(sheetId, "${SheetSpec.SHEET_TRANSACTIONS}!A:A", row)
                }.getOrElse { err ->
                    txnRepo.upsert(t.copy(syncStatus = SyncState.FAILED, pushError = err.message))
                    return@forEach
                }
                txnRepo.upsert(
                    t.copy(
                        sheetRowIndex = newRowIdx,
                        syncStatus = SyncState.SYNCED,
                        pushError = null,
                        lastSyncedSnapshot = RowMapper.rowToSnapshot(row, SheetSpec.TRANSACTIONS_COL_COUNT),
                    )
                )
                count++
            }
        }

        // Membership push: we re-fetch the existing row cells so that columns
        // outside our FY map are preserved. Minimal correctness approach:
        // fetch a narrow batchGet for those specific row numbers.
        val dirtyMembership = membershipRepo.dirty()
        val membershipUpdates = mutableListOf<SheetsApi.RangeUpdate>()
        val membershipFreshPayloads = mutableListOf<Pair<MembershipRowEntity, List<String>>>()
        if (dirtyMembership.isNotEmpty()) {
            val ranges = dirtyMembership.mapNotNull { it.sheetRowIndex }.map {
                "${SheetSpec.SHEET_MEMBERSHIP}!A$it:ZZ$it"
            }
            val current = if (ranges.isNotEmpty())
                runCatching { sheets.batchGet(sheetId, ranges) }.getOrElse { emptyList() }
            else emptyList()

            val hdr = runCatching {
                sheets.batchGet(sheetId, listOf(SheetSpec.MEMBERSHIP_HEADER_RANGE))
            }.getOrNull()?.firstOrNull()?.firstOrNull().orEmpty()
            val fyCols = FinancialYear.detectColumns(hdr)

            dirtyMembership.forEachIndexed { idx, m ->
                val existingRow = current.getOrNull(idx)?.firstOrNull().orEmpty()
                val row = RowMapper.Membership.toRow(m, fyCols, existingRow)
                if (m.sheetRowIndex != null) {
                    val endCol = ColumnLetters.toLetters(row.size - 1)
                    membershipUpdates += SheetsApi.RangeUpdate(
                        range = "${SheetSpec.SHEET_MEMBERSHIP}!A${m.sheetRowIndex}:$endCol${m.sheetRowIndex}",
                        values = listOf(row),
                    )
                    membershipFreshPayloads += m to row
                } else {
                    val newRowIdx = runCatching {
                        sheets.valuesAppend(sheetId, "${SheetSpec.SHEET_MEMBERSHIP}!A:A", row)
                    }.getOrElse { err ->
                        membershipRepo.upsert(m.copy(syncStatus = SyncState.FAILED, pushError = err.message))
                        return@forEachIndexed
                    }
                    membershipRepo.upsert(
                        m.copy(
                            sheetRowIndex = newRowIdx,
                            syncStatus = SyncState.SYNCED,
                            pushError = null,
                            lastSyncedSnapshot = m.feesJson,
                        )
                    )
                    count++
                }
            }
        }

        val allUpdates = memberUpdates + txnUpdates + membershipUpdates
        if (allUpdates.isNotEmpty()) {
            try {
                sheets.valuesBatchUpdate(sheetId, allUpdates)
                // Mark all as synced + refresh snapshots.
                memberFreshPayloads.forEach { (m, row) ->
                    memberRepo.upsert(
                        m.copy(
                            syncStatus = SyncState.SYNCED,
                            pushError = null,
                            lastSyncedSnapshot = RowMapper.rowToSnapshot(row, SheetSpec.MEMBERS_COL_COUNT),
                        )
                    )
                    count++
                }
                txnFreshPayloads.forEach { (t, row) ->
                    txnRepo.upsert(
                        t.copy(
                            syncStatus = SyncState.SYNCED,
                            pushError = null,
                            lastSyncedSnapshot = RowMapper.rowToSnapshot(row, SheetSpec.TRANSACTIONS_COL_COUNT),
                        )
                    )
                    count++
                }
                membershipFreshPayloads.forEach { (m, _) ->
                    membershipRepo.upsert(
                        m.copy(
                            syncStatus = SyncState.SYNCED,
                            pushError = null,
                            lastSyncedSnapshot = m.feesJson,
                        )
                    )
                    count++
                }
            } catch (t: Throwable) {
                Log.w("SyncEngine", "batchUpdate failed: ${t.message}")
                memberFreshPayloads.forEach { (m, _) ->
                    memberRepo.upsert(m.copy(syncStatus = SyncState.FAILED, pushError = t.message))
                }
                txnFreshPayloads.forEach { (txn, _) ->
                    txnRepo.upsert(txn.copy(syncStatus = SyncState.FAILED, pushError = t.message))
                }
                membershipFreshPayloads.forEach { (m, _) ->
                    membershipRepo.upsert(m.copy(syncStatus = SyncState.FAILED, pushError = t.message))
                }
            }
        }

        return count
    }

    private fun rowAsMap(row: List<String>, colCount: Int): Map<String, String> =
        buildMap {
            for (i in 0 until colCount) {
                put(RowMapper.columnLetter(i), row.getOrElse(i) { "" })
            }
        }
}
