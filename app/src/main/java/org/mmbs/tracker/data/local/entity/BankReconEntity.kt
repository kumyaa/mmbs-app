package org.mmbs.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.mmbs.tracker.data.local.SyncState

/**
 * Mirrors one row in the Bank Reconciliation sheet (Phase C writes to this).
 * Schema is intentionally loose: one (statementPeriod, reconDate) row per
 * reconciliation run with the standard statement figures.
 */
@Entity(tableName = "bank_recon")
data class BankReconEntity(
    @PrimaryKey val reconId: String,              // synthetic: e.g., "REC-2026-04"
    val statementFrom: String,
    val statementTo: String,
    val openingBalance: String,
    val totalCredits: String,
    val totalDebits: String,
    val bankClosing: String,
    val appClosing: String,
    val depositsInTransit: String,
    val outstandingPayments: String,
    val reconciledBalance: String,
    val reconDate: String,
    val notes: String,

    // --- sync metadata ---
    val sheetRowIndex: Int? = null,
    val syncStatus: SyncState = SyncState.SYNCED,
    val lastLocalModifiedAt: Long = 0L,
    val lastSyncedSnapshot: String = "",
    val pushError: String? = null,
)
