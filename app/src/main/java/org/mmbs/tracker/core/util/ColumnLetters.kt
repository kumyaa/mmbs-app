package org.mmbs.tracker.core.util

/**
 * A1-notation column helpers. Column index is 0-based (A=0, B=1, ..., Z=25,
 * AA=26, ...). Sheets API v4 uses 1-based column indexes in GridRange/DataRange
 * objects but uses A1 letters in values.* endpoints — this util handles both.
 */
object ColumnLetters {

    /** 0-based column index -> "A", "Z", "AA", "AB", ... */
    fun toLetters(colIndex: Int): String {
        require(colIndex >= 0) { "colIndex must be >= 0, got $colIndex" }
        var n = colIndex
        val sb = StringBuilder()
        while (true) {
            val rem = n % 26
            sb.append('A' + rem)
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.reverse().toString()
    }

    /** "A" -> 0, "AA" -> 26. Case-insensitive. Returns -1 on bad input. */
    fun toIndex(letters: String): Int {
        if (letters.isBlank()) return -1
        var n = 0
        for (ch in letters.trim().uppercase()) {
            if (ch !in 'A'..'Z') return -1
            n = n * 26 + (ch - 'A' + 1)
        }
        return n - 1
    }
}
