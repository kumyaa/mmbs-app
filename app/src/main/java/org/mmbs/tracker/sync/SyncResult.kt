package org.mmbs.tracker.sync

/**
 * Structured outcome of a sync run. Surfaced by SyncEngine so the UI can
 * present S-03 progress and surface conflicts for S-04.
 */
data class SyncResult(
    val pulled: Int = 0,
    val pushed: Int = 0,
    val conflicts: List<ConflictRow> = emptyList(),
    val errors: List<String> = emptyList(),
    val sheetModifiedAt: Long = 0L,
    val tookMs: Long = 0L,
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

/**
 * One row that couldn't be reconciled automatically. The UI (S-04) renders
 * the field-level diffs and asks the user which side wins.
 */
data class ConflictRow(
    val table: String,           // "members", "transactions", ...
    val rowKey: String,          // memberId / txnId / ...
    val sheetRowIndex: Int?,
    val diffs: List<RowConflict.FieldDiff>,
)
