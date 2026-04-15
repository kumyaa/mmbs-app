package org.mmbs.tracker.data.local

/** Per-row sync status. Drives the green/amber/red icons in the UI. */
enum class SyncState {
    /** Row matches the last-pulled snapshot AND has been pushed to sheet. */
    SYNCED,
    /** Row was modified locally and is waiting for push. */
    PENDING,
    /** Last push failed. [CommonSyncFields.pushError] has details. */
    FAILED,
}
