package org.mmbs.tracker.core.util

/** Extracts the spreadsheet ID from either a raw ID or a Google Sheets/Drive URL. */
object SpreadsheetIdParser {

    private val URL_PATTERN = Regex("""/d/([A-Za-z0-9_-]{20,})""")

    /**
     * Returns the bare spreadsheet ID or null if the input doesn't look like one.
     * Accepts:
     *   - 1AbCdE..._-XyZ     (bare ID, 20+ chars from [A-Za-z0-9_-])
     *   - https://docs.google.com/spreadsheets/d/<ID>/edit...
     *   - https://drive.google.com/file/d/<ID>/view...
     */
    fun parse(input: String?): String? {
        val s = input?.trim().orEmpty()
        if (s.isEmpty()) return null
        URL_PATTERN.find(s)?.let { return it.groupValues[1] }
        if (s.matches(Regex("""[A-Za-z0-9_-]{20,}"""))) return s
        return null
    }
}
