package org.mmbs.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.mmbs.tracker.data.local.SyncState

/**
 * Mirrors one row in the Transactions sheet.
 *
 * Columns (from NGO_Accounting_System_v4.xlsx Transactions sheet):
 *   Txn ID | Date | Category | Sub-Category | Description | Member ID |
 *   Account Code | Receipt (₹) | Payment (₹) | Running Balance |
 *   Payment Mode | Reference | Reconciled | Recon Date | Notes
 *
 * We store all numeric fields as strings to preserve the exact sheet
 * representation (e.g. "500.00" vs "500") — we parse to Double in-memory only
 * when running-balance math needs it.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val txnId: String,              // TXN-XXXX

    val date: String,                            // DD-MMM-YYYY
    val category: String,
    val subCategory: String,
    val description: String,
    val memberId: String,
    val accountCode: String,
    val receipt: String,                         // ₹ income — may be blank
    val payment: String,                         // ₹ expense — may be blank
    val runningBalance: String,                  // computed, written back to sheet
    val paymentMode: String,
    val reference: String,
    val reconciled: String,                      // blank or "Y"
    val reconDate: String,                       // DD-MMM-YYYY
    val notes: String,

    // --- sync metadata ---
    val sheetRowIndex: Int? = null,
    val syncStatus: SyncState = SyncState.SYNCED,
    val lastLocalModifiedAt: Long = 0L,
    val lastSyncedSnapshot: String = "",
    val pushError: String? = null,
)
