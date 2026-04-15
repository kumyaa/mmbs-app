package org.mmbs.tracker

import android.content.Context
import org.mmbs.tracker.core.auth.ServiceAccountAuth
import org.mmbs.tracker.core.net.DriveApi
import org.mmbs.tracker.core.net.HttpModule
import org.mmbs.tracker.core.net.SheetsApi
import org.mmbs.tracker.core.util.Prefs
import org.mmbs.tracker.data.local.AppDatabase
import org.mmbs.tracker.data.repo.AppUsersRepo
import org.mmbs.tracker.data.repo.BankReconRepo
import org.mmbs.tracker.data.repo.ConfigRepo
import org.mmbs.tracker.data.repo.MemberRepo
import org.mmbs.tracker.data.repo.MembershipRepo
import org.mmbs.tracker.data.repo.TransactionRepo
import org.mmbs.tracker.domain.model.Role
import org.mmbs.tracker.sync.SyncEngine

/**
 * Manual DI. One instance per process — populated by [init] from
 * [App.onCreate]. Lazy to avoid touching the DB on cold start if unneeded.
 */
object ServiceLocator {

    @Volatile private var _context: Context? = null
    val isReady: Boolean get() = _context != null

    // Core singletons ------------------------------------------------------

    val prefs: Prefs by lazy { Prefs(requireCtx()) }
    val db: AppDatabase by lazy { AppDatabase.get(requireCtx()) }
    val auth: ServiceAccountAuth by lazy { ServiceAccountAuth(requireCtx(), HttpModule.client) }
    val sheets: SheetsApi by lazy { SheetsApi(auth) }
    val drive: DriveApi by lazy { DriveApi(auth) }

    // Repositories ---------------------------------------------------------

    val memberRepo: MemberRepo by lazy { MemberRepo(db.memberDao()) }
    val membershipRepo: MembershipRepo by lazy { MembershipRepo(db.membershipRowDao()) }
    val transactionRepo: TransactionRepo by lazy { TransactionRepo(db.transactionDao()) }
    val bankReconRepo: BankReconRepo by lazy { BankReconRepo(db.bankReconDao()) }
    val configRepo: ConfigRepo by lazy { ConfigRepo(db.configKvDao()) }
    val appUsersRepo: AppUsersRepo by lazy { AppUsersRepo(db.appUserDao()) }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            sheets = sheets,
            drive = drive,
            prefs = prefs,
            memberRepo = memberRepo,
            membershipRepo = membershipRepo,
            txnRepo = transactionRepo,
            reconRepo = bankReconRepo,
            configRepo = configRepo,
            appUsersRepo = appUsersRepo,
        )
    }

    // Session role — cached in memory after sign-in.
    @Volatile var currentRole: Role? = null

    fun init(context: Context) {
        _context = context.applicationContext
        // Restore role from prefs on cold start.
        currentRole = Role.fromSheetValue(prefs.userRole)
    }

    private fun requireCtx(): Context =
        _context ?: error("ServiceLocator not initialized — call init() from Application.onCreate")
}
