package org.mmbs.tracker.ui.member

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.core.util.WhatsApp
import org.mmbs.tracker.domain.fy.FinancialYear
import org.mmbs.tracker.domain.model.FyCell
import org.mmbs.tracker.sync.PushWorker
import org.mmbs.tracker.sync.RowMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S-10 Record payment. Writes one FY cell on the member's Membership Tracker
 * row and then offers to ping the member on WhatsApp.
 *
 * FY selection is a dropdown of all FYs the sheet knows about plus the
 * current and next FY (so advance payments in Jan-Mar work without editing
 * the sheet first). If the caller passes an `fy` argument, that FY is
 * pre-selected AND the existing cell values for that year are pre-filled,
 * making this screen doubly useful as "edit payment for FY X".
 */
class RecordPaymentFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_record_payment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val memberId = arguments?.getString("memberId").orEmpty()
        val argFy = arguments?.getString("fy")?.takeIf { it.isNotBlank() }

        val memberLabel = view.findViewById<TextView>(R.id.memberLabel)
        val fySpinner = view.findViewById<Spinner>(R.id.fy)
        val amount = view.findViewById<EditText>(R.id.amount)
        val date = view.findViewById<EditText>(R.id.date)
        val receipt = view.findViewById<EditText>(R.id.receipt)
        val status = view.findViewById<EditText>(R.id.status)
        val save = view.findViewById<Button>(R.id.saveButton)
        val wa = view.findViewById<Button>(R.id.waButton)

        // Populate the FY dropdown from the union of (sheet-detected FYs +
        // current FY + next FY). Defaults to the current FY; if the caller
        // passed an fy arg, select that instead.
        val known = ServiceLocator.prefs.knownFyLabels
        val opts = FinancialYear.dropdownOptions(known)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            opts.labels,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        fySpinner.adapter = adapter
        val initialIndex = argFy?.let { FinancialYear.normalize(it) }
            ?.let { opts.labels.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?: opts.defaultIndex
        fySpinner.setSelection(initialIndex)

        date.setText(SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date()))
        status.setText("Paid")

        // Load the member + any existing cell for the pre-selected FY so the
        // user sees current values (supports the "edit" flow from Member
        // Detail's FY history list).
        viewLifecycleOwner.lifecycleScope.launch {
            val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
            memberLabel.text = "${m.primaryName} · ${m.memberId}"

            val selectedFy = opts.labels.getOrNull(initialIndex) ?: return@launch
            val membership = ServiceLocator.membershipRepo.get(memberId)
            val existing = membership?.let {
                RowMapper.Membership.parseFees(it.feesJson)[selectedFy]
            }
            if (existing != null) {
                status.setText(existing.status)
                amount.setText(existing.amount)
                date.setText(existing.date.ifBlank {
                    SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date())
                })
                receipt.setText(existing.receipt)
            }
        }

        save.setOnClickListener {
            val fyLabel = fySpinner.selectedItem?.toString().orEmpty()
            if (fyLabel.isBlank()) {
                Toast.makeText(requireContext(), R.string.payment_fy_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cell = FyCell(
                status = status.text.toString().trim(),
                amount = amount.text.toString().trim(),
                date = date.text.toString().trim(),
                receipt = receipt.text.toString().trim(),
            )
            viewLifecycleOwner.lifecycleScope.launch {
                val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
                ServiceLocator.membershipRepo.recordPayment(
                    memberId = m.memberId,
                    primaryName = m.primaryName,
                    fyLabel = fyLabel,
                    cell = cell,
                )
                PushWorker.enqueue(requireContext())
                Toast.makeText(requireContext(), R.string.payment_saved, Toast.LENGTH_SHORT).show()
            }
        }

        wa.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
                val msg = WhatsApp.paymentReceiptMessage(
                    memberName = m.primaryName,
                    fyLabel = fySpinner.selectedItem?.toString().orEmpty(),
                    amount = amount.text.toString().trim(),
                    date = date.text.toString().trim(),
                    receiptNo = receipt.text.toString().trim(),
                )
                WhatsApp.send(requireContext(), m.primaryMobile, msg)
            }
        }

        // Drop back to detail after a successful save+navigate is intentional
        // — user can add another payment without re-navigating if they want.
        save.setOnLongClickListener {
            findNavController().popBackStack(); true
        }
    }
}
