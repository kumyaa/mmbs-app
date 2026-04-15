package org.mmbs.tracker.domain.model

import kotlinx.serialization.Serializable

/**
 * A single FY block in the Membership Tracker sheet.
 * Four columns per FY: Fee Status | Amount | Date Paid | Receipt #.
 *
 * Empty strings are used in place of nulls so round-tripping to the sheet is
 * lossless — a blank sheet cell is distinct from an absent FY only at the map
 * level (an absent key means the FY doesn't exist yet in the sheet).
 */
@Serializable
data class FyCell(
    val status: String = "",
    val amount: String = "",
    val date: String = "",
    val receipt: String = "",
) {
    val isBlank: Boolean
        get() = status.isBlank() && amount.isBlank() && date.isBlank() && receipt.isBlank()
}
