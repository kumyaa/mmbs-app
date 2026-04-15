package org.mmbs.tracker.domain.model

/**
 * App roles from the AppUsers sheet (PRD §1.3, §2.2).
 *
 * Strings in the sheet must match exactly — case-insensitive match at load
 * time is fine, but the values we compare against are normalized via
 * [fromSheetValue].
 */
enum class Role(val label: String) {
    TREASURER("Treasurer"),
    COMMITTEE_MEMBER("Committee Member"),
    AUDITOR("Auditor"),
    ;

    /** True if this role is allowed to add/edit/delete. */
    val canWrite: Boolean
        get() = this == TREASURER || this == COMMITTEE_MEMBER

    /** True if this role sees delete buttons (Treasurer only per PRD §2.2). */
    val canDelete: Boolean
        get() = this == TREASURER

    companion object {
        fun fromSheetValue(raw: String?): Role? {
            val v = raw?.trim()?.lowercase() ?: return null
            return values().firstOrNull { it.label.lowercase() == v }
        }
    }
}
