package org.mmbs.tracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.mmbs.tracker.data.local.dao.AppUserDao
import org.mmbs.tracker.data.local.dao.BankReconDao
import org.mmbs.tracker.data.local.dao.ConfigKvDao
import org.mmbs.tracker.data.local.dao.MemberDao
import org.mmbs.tracker.data.local.dao.MembershipRowDao
import org.mmbs.tracker.data.local.dao.TransactionDao
import org.mmbs.tracker.data.local.entity.AppUserEntity
import org.mmbs.tracker.data.local.entity.BankReconEntity
import org.mmbs.tracker.data.local.entity.ConfigKvEntity
import org.mmbs.tracker.data.local.entity.MemberEntity
import org.mmbs.tracker.data.local.entity.MembershipRowEntity
import org.mmbs.tracker.data.local.entity.TransactionEntity

@Database(
    entities = [
        MemberEntity::class,
        MembershipRowEntity::class,
        TransactionEntity::class,
        BankReconEntity::class,
        ConfigKvEntity::class,
        AppUserEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memberDao(): MemberDao
    abstract fun membershipRowDao(): MembershipRowDao
    abstract fun transactionDao(): TransactionDao
    abstract fun bankReconDao(): BankReconDao
    abstract fun configKvDao(): ConfigKvDao
    abstract fun appUserDao(): AppUserDao

    companion object {
        private const val DB_NAME = "mmbs.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                // Phase A ships v1. Destructive fallback is acceptable for
                // pre-release installs; real migrations will be added before
                // any version bump that reaches users.
                .fallbackToDestructiveMigration()
                .build()
    }
}
