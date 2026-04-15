package org.mmbs.tracker.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.core.auth.ServiceAccountAuth.AuthNotConfiguredException
import org.mmbs.tracker.core.util.SpreadsheetIdParser

/**
 * S-02 First-launch setup. User pastes a Sheet URL or ID. We validate by
 * calling Drive files.get. If successful, we kick a first sync (S-03) which
 * will populate AppUsers, from which we can enforce the access check.
 */
class SetupFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_setup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val input = view.findViewById<EditText>(R.id.sheetInput)
        val errorText = view.findViewById<TextView>(R.id.errorText)
        val connect = view.findViewById<Button>(R.id.connectButton)
        val progress = view.findViewById<ProgressBar>(R.id.progress)

        ServiceLocator.prefs.spreadsheetId?.let { input.setText(it) }

        connect.setOnClickListener {
            val parsed = SpreadsheetIdParser.parse(input.text.toString())
            if (parsed == null) {
                errorText.text = getString(R.string.setup_error_invalid)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            errorText.visibility = View.GONE
            connect.isEnabled = false
            progress.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    ServiceLocator.drive.getSpreadsheetMetadata(parsed)
                    ServiceLocator.prefs.spreadsheetId = parsed
                    findNavController().navigate(R.id.syncProgressFragment)
                } catch (t: AuthNotConfiguredException) {
                    showError(errorText, getString(R.string.setup_error_service_account))
                } catch (t: Throwable) {
                    showError(errorText, getString(R.string.setup_error_no_access) + "\n" + t.message)
                } finally {
                    connect.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun showError(label: TextView, msg: String) {
        label.text = msg
        label.visibility = View.VISIBLE
    }
}
