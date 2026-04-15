package org.mmbs.tracker.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator

/**
 * S-03 First-run sync. Kicks one full SyncEngine cycle. On success, checks
 * the signed-in email against AppUsers; if not allowed → blocks with an
 * error. If conflicts → jumps to the Conflict resolution screen. Otherwise →
 * Home.
 */
class SyncProgressFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_sync_progress, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val status = view.findViewById<TextView>(R.id.status)
        val retry = view.findViewById<Button>(R.id.retryButton)

        fun run() {
            progress.visibility = View.VISIBLE
            retry.visibility = View.GONE
            status.text = getString(R.string.sync_in_progress)

            viewLifecycleOwner.lifecycleScope.launch {
                val result = try {
                    ServiceLocator.syncEngine.syncNow()
                } catch (t: Throwable) {
                    progress.visibility = View.GONE
                    status.text = getString(R.string.sync_error_fmt, t.message.orEmpty())
                    retry.visibility = View.VISIBLE
                    return@launch
                }
                progress.visibility = View.GONE

                // If the sync itself had errors (auth, network, bad range, ...)
                // surface the *real* message first — otherwise a blank local DB
                // would look like "Access not authorised" which is misleading.
                if (result.errors.isNotEmpty()) {
                    status.text = getString(
                        R.string.sync_error_fmt,
                        result.errors.joinToString("; "),
                    )
                    retry.visibility = View.VISIBLE
                    return@launch
                }

                // Access check: AppUsers must contain the signed-in email.
                val email = ServiceLocator.prefs.userEmail.orEmpty()
                val role = ServiceLocator.appUsersRepo.role(email)
                if (role == null) {
                    status.text = getString(R.string.setup_error_not_authorised)
                    retry.visibility = View.VISIBLE
                    return@launch
                }
                ServiceLocator.prefs.userRole = role.label
                ServiceLocator.currentRole = role

                when {
                    result.hasConflicts -> {
                        findNavController().navigate(R.id.conflictFragment)
                    }
                    else -> {
                        findNavController().navigate(
                            R.id.homeFragment,
                            null,
                            androidx.navigation.navOptions {
                                popUpTo(R.id.signInFragment) { inclusive = true }
                            }
                        )
                    }
                }
            }
        }

        retry.setOnClickListener { run() }
        run()
    }
}
