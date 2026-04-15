package org.mmbs.tracker.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator

/**
 * S-01 Sign In. Plain email text field; no OAuth. The email will be checked
 * against the AppUsers list after the spreadsheet is connected in S-02.
 */
class SignInFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_sign_in, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val input = view.findViewById<EditText>(R.id.emailInput)
        val errorText = view.findViewById<TextView>(R.id.errorText)
        val button = view.findViewById<Button>(R.id.continueButton)

        ServiceLocator.prefs.userEmail?.let { input.setText(it) }

        button.setOnClickListener {
            val email = input.text.toString().trim().lowercase()
            val error = when {
                email.isEmpty() -> getString(R.string.signin_error_empty)
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    getString(R.string.signin_error_format)
                else -> null
            }
            if (error != null) {
                errorText.text = error
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            ServiceLocator.prefs.userEmail = email

            val dest = if (ServiceLocator.prefs.spreadsheetId.isNullOrBlank())
                R.id.setupFragment else R.id.syncProgressFragment
            findNavController().navigate(dest)
        }
    }
}
