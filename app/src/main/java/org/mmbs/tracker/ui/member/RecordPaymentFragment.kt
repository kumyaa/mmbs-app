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
 * row and offers to ping the member on WhatsApp.
 *
 * FY selection is a dropdown; the caller may pass an `fy` arg to pre-select
 * a specific year. The Status field is also a dropdown (New / Renewed /
 * Unpaid / Lapsed). When editing an existing cell whose status doesn't match
 * any standard option (legacy "Paid" etc.), that value is prepended to the
 * list so it's preserved unless the treasurer explicitly changes it.
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
        val statusSpinner = view.findViewById<Spinner>(R.id.status)
        val save = view.findViewById<Button>(R.id.saveButton)
        val wa = view.findViewById<Button>(R.id.waButton)

        // FY dropdown
        val known = ServiceLocator.prefs.knownFyLabels
        val opts = FinancialYear.dropdownOptions(known)
        fySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            opts.labels,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val initialFyIndex = argFy?.let { FinancialYear.normalize(it) }
            ?.let { opts.labels.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?: opts.defaultIndex
        fySpinner.setSelection(initialFyIndex)

        // Status dropdown — default adapter with standard options; may be
        // rebuilt below if the existing cell carries an unknown legacy value.
        bindStatusSpinner(statusSpinner, STATUS_OPTIONS, DEFAULT_STATUS)

        date.setText(SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date()))

        // Load member + existing cell so fields pre-fill when editing.
        viewLifecycleOwner.lifecycleScope.launch {
            val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
            memberLabel.text = "${m.primaryName} · ${m.memberId}"

            val selectedFy = opts.labels.getOrNull(initialFyIndex) ?: return@launch
            val membership = ServiceLocator.membershipRepo.get(memberId)
            val existing = membership?.let {
                RowMapper.Membership.parseFees(it.feesJson)[selectedFy]
            }
            if (existing != null) {
                amount.setText(existing.amount)
                date.setText(existing.date.ifBlank {
                    SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date())
                })
                receipt.setText(existing.receipt)
                // If legacy value (e.g. "Paid") isn't in standard list,
                // prepend it so it's visible and not silently overwritten.
                val statusOptions = if (existing.status.isNotBlank() &&
                    existing.status !in STATUS_OPTIONS
                ) {
                    listOf(existing.status) + STATUS_OPTIONS
                } else {
                    STATUS_OPTIONS
                }
                bindStatusSpinner(
                    statusSpinner,
                    statusOptions,
                    existing.status.ifBlank { DEFAULT_STATUS },
                )
            }
        }

        save.setOnClickListener {
            val fyLabel = fySpinner.selectedItem?.toString().orEmpty()
            if (fyLabel.isBlank()) {
                Toast.makeText(requireContext(), R.string.payment_fy_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cell = FyCell(
                status = statusSpinner.selectedItem?.toString().orEmpty(),
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

        save.setOnLongClickListener { findNavController().popBackStack(); true }
    }

    private fun bindStatusSpinner(
        spinner: Spinner,
        options: List<String>,
        selected: String,
    ) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            options,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.adapter = adapter
        val idx = options.indexOf(selected).takeIf { it >= 0 } ?: 0
        spinner.setSelection(idx)
    }

    companion object {
        val STATUS_OPTIONS = listOf("New", "Renewed", "Unpaid", "Lapsed")
        const val DEFAULT_STATUS = "Renewed"
    }
}
