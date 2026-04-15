package org.mmbs.tracker.sync

/**
 * Central description of every sheet we sync with. Keeping sheet names,
 * data-start rows and column counts in one place so RowMapper / SyncEngine
 * stay consistent.
 *
 * Data-start rows follow PRD §9:
 *   - Members:             header rows 1-3, data starts at row 4
 *   - Membership Tracker:  FY headers row 1, sub-headers rows 2-3, data row 4
 *   - Transactions:        header rows 1-3, data row 4 (row 4 is TXN-0000 opening balance)
 *   - Bank Reconciliation: header row 1, data row 2
 *   - Config:              labels in row 1, values start row 2
 *   - AppUsers:            header row 1, data row 2
 */
object SheetSpec {

    const val SHEET_MEMBERS = "Members"
    const val SHEET_MEMBERSHIP = "Membership Tracker"
    const val SHEET_TRANSACTIONS = "Transactions"
    const val SHEET_BANK_RECON = "Bank Reconciliation"
    const val SHEET_CONFIG = "Config"
    const val SHEET_APP_USERS = "AppUsers"

    const val MEMBERS_DATA_START_ROW = 4
    const val MEMBERSHIP_DATA_START_ROW = 4
    const val MEMBERSHIP_HEADER_ROW = 1          // 1-based row index where "FY YYYY-YY" labels live
    const val TRANSACTIONS_DATA_START_ROW = 4
    const val BANK_RECON_DATA_START_ROW = 2
    const val CONFIG_DATA_START_ROW = 2
    const val APP_USERS_DATA_START_ROW = 2

    // Column extents (inclusive, A1-notation).
    // Members spans A..X (24 cols).
    const val MEMBERS_RANGE = "Members!A1:X"

    // Membership Tracker can have arbitrary FY blocks; we read a wide strip
    // and let RowMapper slice by detected FY columns. Row 1 holds "FY YYYY-YY"
    // headers (Phase A detects and caches them).
    const val MEMBERSHIP_HEADER_RANGE = "Membership Tracker!A1:ZZ1"
    const val MEMBERSHIP_DATA_RANGE = "Membership Tracker!A4:ZZ"

    // Transactions spans A..O (15 cols) per PRD §9.3.
    const val TRANSACTIONS_RANGE = "Transactions!A1:O"

    const val BANK_RECON_RANGE = "Bank Reconciliation!A1:M"
    const val CONFIG_RANGE = "Config!A1:J"
    const val APP_USERS_RANGE = "AppUsers!A1:B"

    const val MEMBERS_COL_COUNT = 24               // A..X
    const val TRANSACTIONS_COL_COUNT = 15           // A..O
    const val BANK_RECON_COL_COUNT = 13             // A..M
    const val APP_USERS_COL_COUNT = 2               // A..B
    const val MEMBERSHIP_FIXED_LEAD_COLS = 2        // A: memberId, B: primaryName
}
