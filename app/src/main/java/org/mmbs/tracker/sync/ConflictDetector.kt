package org.mmbs.tracker.sync

/**
 * Per-row conflict detection (PRD §3.4).
 *
 *   clean       remote == snapshot, local == SYNCED                → just apply remote
 *   local-only  remote == snapshot, local PENDING/FAILED            → push local
 *   remote-only remote != snapshot, local == SYNCED                 → apply remote
 *   both        remote != snapshot, local PENDING/FAILED, different → conflict (S-04)
 *
 * "remote row" here is the list of cell strings freshly pulled from the sheet
 * for a given row. "snapshot" is the map parsed out of
 * [lastSyncedSnapshot]. The comparison is stable under column reordering
 * because we build snapshots keyed by column letter.
 */
sealed interface RowConflict {
    data class FieldDiff(
        val column: String,
        val snapshotValue: String,
        val remoteValue: String,
        val localValue: String,
    )

    /** Remote unchanged since last sync; and local either clean or already
     *  pushed. Nothing to do. */
    object Clean : RowConflict

    /** Only the local side has changes — push needed. */
    object LocalOnly : RowConflict

    /** Only the remote side has changes — overwrite local with remote. */
    data class RemoteOnly(val remoteCells: Map<String, String>) : RowConflict

    /** Both sides changed; user must resolve. */
    data class Both(val diffs: List<FieldDiff>) : RowConflict
}

object ConflictDetector {

    /**
     * @param snapshotJson   last-pulled sheet snapshot, stored on the entity
     * @param remoteRow      freshly-pulled row cells (length == colCount)
     * @param localCells     map of column letter -> current local cell values
     * @param isLocalDirty   true if syncStatus != SYNCED
     */
    fun detect(
        snapshotJson: String,
        remoteRow: List<String>,
        localCells: Map<String, String>,
        colCount: Int,
        isLocalDirty: Boolean,
    ): RowConflict {
        val snapshot = RowMapper.snapshotToMap(snapshotJson)
        val remote = buildMap<String, String> {
            for (i in 0 until colCount) {
                put(RowMapper.columnLetter(i), remoteRow.getOrElse(i) { "" })
            }
        }

        val remoteChanged = remote != snapshot && snapshot.isNotEmpty()
        val localChanged = isLocalDirty

        return when {
            !remoteChanged && !localChanged -> RowConflict.Clean
            remoteChanged && !localChanged -> RowConflict.RemoteOnly(remote)
            !remoteChanged && localChanged -> RowConflict.LocalOnly
            else -> {
                // Both changed — diff columns that differ between remote and local.
                val diffs = (remote.keys + localCells.keys).sorted().mapNotNull { col ->
                    val r = remote[col].orEmpty()
                    val l = localCells[col].orEmpty()
                    if (r == l) null
                    else RowConflict.FieldDiff(
                        column = col,
                        snapshotValue = snapshot[col].orEmpty(),
                        remoteValue = r,
                        localValue = l,
                    )
                }
                if (diffs.isEmpty()) RowConflict.Clean else RowConflict.Both(diffs)
            }
        }
    }
}
