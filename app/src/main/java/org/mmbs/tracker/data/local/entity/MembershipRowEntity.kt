package org.mmbs.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.mmbs.tracker.data.local.SyncState

/**
 * One row per member from the Membership Tracker sheet.
 *
 * Each FY in the sheet occupies 4 columns (Fee Status, Amount, Date Paid,
 * Receipt #). The number and positions of FY blocks is detected at sync time
 * by scanning row 2 for "FY YYYY-YY" headers (PRD §9.2).
 *
 * We store the full FY map as a JSON object keyed by FY label — simpler than
 * adding dynamic columns to the schema.
 *
 *   feesJson = {"FY 2025-26":{"status":"Paid","amount":"500","date":"12-Apr-2025","receipt":"R-123"}, ...}
 */
@Entity(tableName = "membership_rows")
data class MembershipRowEntity(
    @PrimaryKey val memberId: String,        // matches MemberEntity.memberId

    val primaryName: String,                 // duplicated for offline display
    val feesJson: String,                    // { FY_LABEL -> FyCell } as JSON

    // --- sync metadata ---
    val sheetRowIndex: Int? = null,
    val syncStatus: SyncState = SyncState.SYNCED,
    val lastLocalModifiedAt: Long = 0L,
    val lastSyncedSnapshot: String = "",
    val pushError: String? = null,
)
