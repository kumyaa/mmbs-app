package org.mmbs.tracker.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * S-05 Home dashboard. KPI cards from local DB + last-sync label + a
 * pull-to-refresh that runs SyncEngine.
 */
class HomeFragment : Fragment() {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val kpiMembers = view.findViewById<TextView>(R.id.kpiMembers)
        val kpiActive = view.findViewById<TextView>(R.id.kpiActive)
        val kpiInactive = view.findViewById<TextView>(R.id.kpiInactive)
        val kpiSuspended = view.findViewById<TextView>(R.id.kpiSuspended)
        val lastSync = view.findViewById<TextView>(R.id.lastSync)
        val stale = view.findViewById<TextView>(R.id.staleBanner)
        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val signOut = view.findViewById<TextView>(R.id.signOutButton)
        val quickMembers = view.findViewById<Button>(R.id.quickMembers)
        val quickSearch = view.findViewById<Button>(R.id.quickSearch)

        fun refreshLocal() {
            viewLifecycleOwner.lifecycleScope.launch {
                val total = ServiceLocator.memberRepo.count()
                val byStatus = ServiceLocator.memberRepo.countByStatus().associate { it.status to it.n }
                kpiMembers.text = getString(R.string.home_members_fmt, total)
                kpiActive.text = getString(R.string.home_active_fmt, byStatus["Active"] ?: 0)
                kpiInactive.text = getString(R.string.home_inactive_fmt, byStatus["Inactive"] ?: 0)
                kpiSuspended.text = getString(R.string.home_suspended_fmt, byStatus["Suspended"] ?: 0)

                val ts = ServiceLocator.prefs.lastSyncEpochMs
                lastSync.text = if (ts <= 0) getString(R.string.home_last_sync_never)
                else getString(R.string.home_last_sync_fmt, dateFmt.format(Date(ts)))

                val isStale = ts > 0 &&
                    System.currentTimeMillis() - ts > TimeUnit.HOURS.toMillis(24)
                stale.visibility = if (isStale) View.VISIBLE else View.GONE
            }
        }

        swipe.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = runCatching { ServiceLocator.syncEngine.syncNow() }.getOrNull()
                swipe.isRefreshing = false
                refreshLocal()
                if (result?.hasConflicts == true) {
                    findNavController().navigate(R.id.conflictFragment)
                }
            }
        }

        signOut.setOnClickListener {
            ServiceLocator.prefs.clearAll()
            ServiceLocator.currentRole = null
            findNavController().navigate(
                R.id.signInFragment,
                null,
                androidx.navigation.navOptions {
                    popUpTo(R.id.homeFragment) { inclusive = true }
                }
            )
        }

        quickMembers.setOnClickListener {
            findNavController().navigate(R.id.memberListFragment)
        }
        quickSearch.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }

        refreshLocal()
    }
}
