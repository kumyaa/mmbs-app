package org.mmbs.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.mmbs.tracker.data.local.SyncState

/**
 * Mirrors the Members sheet (columns A..X per PRD §9.1).
 *
 * Columns A..X:
 *   A Member ID (MM-XXXX)   B Reg Date              C Primary Name
 *   D Mobile Primary        E Email                 F..I FM2 (Name/Rel/Mobile/WaGroup)
 *   J..M FM3                N..Q FM4                R Address
 *   S First Year            T Status                U Total Family
 *   V WA Group Count        W WA Validation (read-only)   X Notes
 *
 * Sync fields: syncStatus/sheetRowIndex/lastLocalModifiedAt drive the icon
 * and the push pipeline. lastSyncedSnapshot is the JSON-encoded snapshot of
 * cells at the last pull — used for per-field conflict detection.
 */
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val memberId: String,          // A — MM-XXXX

    val regDate: String,                       // B — DD-MMM-YYYY string, pass-through
    val primaryName: String,                   // C
    val primaryMobile: String,                 // D
    val email: String,                         // E

    val fm2Name: String, val fm2Rel: String, val fm2Mobile: String, val fm2WaGroup: String,
    val fm3Name: String, val fm3Rel: String, val fm3Mobile: String, val fm3WaGroup: String,
    val fm4Name: String, val fm4Rel: String, val fm4Mobile: String, val fm4WaGroup: String,

    val address: String,                       // R
    val firstYear: String,                     // S — e.g., 2025-26
    val status: String,                        // T — Active/Inactive/Suspended
    val totalFamilyMembers: String,            // U
    val waGroupCount: String,                  // V
    val waValidation: String,                  // W — read-only from sheet
    val notes: String,                         // X

    // --- sync metadata ---
    val sheetRowIndex: Int? = null,
    val syncStatus: SyncState = SyncState.SYNCED,
    val lastLocalModifiedAt: Long = 0L,
    val lastSyncedSnapshot: String = "",       // JSON of last-pulled cell values
    val pushError: String? = null,
)
