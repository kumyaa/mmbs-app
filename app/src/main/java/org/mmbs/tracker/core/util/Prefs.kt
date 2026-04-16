package org.mmbs.tracker.core.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper over SharedPreferences for the app's long-lived keys.
 * Kept tiny — we have maybe 6 scalars to persist.
 */
class Prefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("mmbs", Context.MODE_PRIVATE)

    var userEmail: String?
        get() = sp.getString(KEY_EMAIL, null)
        set(v) = sp.edit().putString(KEY_EMAIL, v).apply()

    var userRole: String?
        get() = sp.getString(KEY_ROLE, null)
        set(v) = sp.edit().putString(KEY_ROLE, v).apply()

    var spreadsheetId: String?
        get() = sp.getString(KEY_SPREADSHEET_ID, null)
        set(v) = sp.edit().putString(KEY_SPREADSHEET_ID, v).apply()

    var lastSyncEpochMs: Long
        get() = sp.getLong(KEY_LAST_SYNC, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_SYNC, v).apply()

    var sheetModifiedTimeEpochMs: Long
        get() = sp.getLong(KEY_SHEET_MODIFIED, 0L)
        set(v) = sp.edit().putLong(KEY_SHEET_MODIFIED, v).apply()

    var fyLabel: String?
        get() = sp.getString(KEY_FY_LABEL, null)
        set(v) = sp.edit().putString(KEY_FY_LABEL, v).apply()

    /**
     * Canonical "FY YYYY-YY" labels detected in the Membership Tracker header
     * at the last successful sync. Stored comma-separated so the setter/getter
     * handles List<String> without needing a JSON dependency for a trivial list.
     * Empty list if never synced or no FY headers present.
     */
    var knownFyLabels: List<String>
        get() = sp.getString(KEY_FY_LABELS, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(v) = sp.edit().putString(KEY_FY_LABELS, v.joinToString(",")).apply()

    fun clearAll() {
        sp.edit().clear().apply()
    }

    /** Clears everything EXCEPT the signed-in email (used on "Change spreadsheet"). */
    fun clearSpreadsheetState() {
        sp.edit()
            .remove(KEY_SPREADSHEET_ID)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_SHEET_MODIFIED)
            .remove(KEY_FY_LABEL)
            .remove(KEY_FY_LABELS)
            .remove(KEY_ROLE)
            .apply()
    }

    companion object {
        private const val KEY_EMAIL = "user_email"
        private const val KEY_ROLE = "user_role"
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_LAST_SYNC = "last_sync_ms"
        private const val KEY_SHEET_MODIFIED = "sheet_modified_ms"
        private const val KEY_FY_LABEL = "fy_label"
        private const val KEY_FY_LABELS = "fy_labels_csv"
    }
}
