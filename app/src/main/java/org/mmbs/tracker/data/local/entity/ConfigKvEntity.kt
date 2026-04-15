package org.mmbs.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Flat key-value cache of Config sheet values the app needs at runtime:
 *   - current FY (Config!C4)
 *   - Status options (column H)
 *   - Fee Status options (column I)
 *   - Category options (column J)
 *   - Payment Mode options (column G)
 *
 * Config is read-only from the app (PRD §1.3, SYNC table).
 */
@Entity(tableName = "config_kv")
data class ConfigKvEntity(
    @PrimaryKey val key: String,
    val value: String,
)
