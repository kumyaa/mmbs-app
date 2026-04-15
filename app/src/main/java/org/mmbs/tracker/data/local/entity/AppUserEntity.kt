package org.mmbs.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached AppUsers sheet. Read-only from the app.
 * role ∈ { "Treasurer", "Committee Member", "Auditor" }.
 */
@Entity(tableName = "app_users")
data class AppUserEntity(
    @PrimaryKey val email: String,   // lowercase, trimmed
    val role: String,
)
