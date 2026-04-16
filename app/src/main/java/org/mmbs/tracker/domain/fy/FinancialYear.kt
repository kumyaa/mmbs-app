package org.mmbs.tracker.domain.fy

import java.util.Calendar

/**
 * MMBS financial year helpers (PRD §9.2).
 *
 * FY labels look like "FY 2025-26" and run 1-Apr → 31-Mar (Indian FY).
 * Membership Tracker sheet row 2 has one "FY YYYY-YY" header above each
 * 4-column block (Fee Status | Amount | Date Paid | Receipt #).
 */
object FinancialYear {

    private val LABEL_RE = Regex("""^\s*FY\s*(\d{4})\s*-\s*(\d{2})\s*$""", RegexOption.IGNORE_CASE)

    /** "FY 2025-26" label for a given starting calendar year (Apr of that year). */
    fun label(startYear: Int): String {
        val endShort = ((startYear + 1) % 100).toString().padStart(2, '0')
        return "FY $startYear-$endShort"
    }

    /** Current FY label based on today's date. April rolls over to the new FY. */
    fun currentLabel(cal: Calendar = Calendar.getInstance()): String {
        val month = cal.get(Calendar.MONTH) + 1 // 1..12
        val year = cal.get(Calendar.YEAR)
        val startYear = if (month >= 4) year else year - 1
        return label(startYear)
    }

    /** The FY that follows [currentLabel]. Useful during Jan-Mar for advance membership. */
    fun nextLabel(cal: Calendar = Calendar.getInstance()): String {
        val current = startYear(currentLabel(cal)) ?: return currentLabel(cal)
        return label(current + 1)
    }

    /**
     * Build the Record-Payment dropdown options: every FY detected in the
     * sheet (passed in) plus the current and next FY, de-duplicated and
     * sorted newest-first. The current FY is returned as the "default"
     * selection index so the caller can pre-select it.
     */
    data class Options(val labels: List<String>, val defaultIndex: Int)

    fun dropdownOptions(
        detected: List<String>,
        cal: Calendar = Calendar.getInstance(),
    ): Options {
        val current = currentLabel(cal)
        val merged = (detected + current + nextLabel(cal))
            .mapNotNull { normalize(it) }
            .distinct()
            .sortedByDescending { startYear(it) ?: -1 }
        val idx = merged.indexOf(current).coerceAtLeast(0)
        return Options(labels = merged, defaultIndex = idx)
    }

    /** Parse a header label to its starting year. null if it doesn't match. */
    fun startYear(label: String): Int? {
        val m = LABEL_RE.matchEntire(label) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    /** Normalize varied casing / spacing to canonical "FY YYYY-YY". */
    fun normalize(label: String): String? {
        val start = startYear(label) ?: return null
        return label(start)
    }

    /**
     * Each detected FY block in the Membership Tracker sheet.
     *
     * @param label        canonical "FY YYYY-YY"
     * @param startColumn  0-based column index of the first of the 4 block columns.
     *                     Columns [startColumn .. startColumn+3] are
     *                     Fee Status | Amount | Date Paid | Receipt #.
     */
    data class FyColumn(val label: String, val startColumn: Int)

    /**
     * Scan the Membership Tracker header row (row 2 in the sheet, which is
     * row index 1 in a 0-based list) for FY YYYY-YY labels. Returns the list
     * in column order.
     *
     * Per PRD §9.2 the FY label sits above the block's first (Fee Status)
     * column; the next 3 columns for that FY carry either empty header cells
     * or sub-headings like "Amount (Rs)".
     */
    fun detectColumns(headerRow: List<String>): List<FyColumn> {
        val out = mutableListOf<FyColumn>()
        var i = 0
        while (i < headerRow.size) {
            val label = normalize(headerRow[i])
            if (label != null) {
                out += FyColumn(label = label, startColumn = i)
                i += 4 // skip the 4-column block
            } else {
                i += 1
            }
        }
        return out
    }
}
