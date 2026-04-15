package org.mmbs.tracker.data.local

import androidx.room.TypeConverter

/** Room type converters. Keeping these flat — no serialised JSON here. */
class Converters {
    @TypeConverter fun syncStateToString(v: SyncState?): String? = v?.name
    @TypeConverter fun stringToSyncState(v: String?): SyncState? = v?.let { SyncState.valueOf(it) }
}
