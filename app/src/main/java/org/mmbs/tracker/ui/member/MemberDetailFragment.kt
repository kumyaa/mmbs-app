package org.mmbs.tracker.ui.member

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.sync.PushWorker
import org.mmbs.tracker.sync.RowMapper

/**
 * S-08 Member detail. Shows the member's fields + their Membership Tracker
 * payments (flattened). Treasurer sees Delete; anyone with write access sees
 * Edit and Record-Payment. Auditor sees the page read-only.
 */
class MemberDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_member_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val memberId = arguments?.getString("memberId").orEmpty()
        val role = ServiceLocator.currentRole

        val name = view.findViewById<TextView>(R.id.memberName)
        val id = view.findViewById<TextView>(R.id.memberId)
        val details = view.findViewById<TextView>(R.id.details)
        val payments = view.findViewById<TextView>(R.id.payments)
        val edit = view.findViewById<Button>(R.id.editButton)
        val record = view.findViewById<Button>(R.id.recordButton)
        val delete = view.findViewById<Button>(R.id.deleteButton)

        ServiceLocator.memberRepo.observe(memberId).observe(viewLifecycleOwner) { m ->
            if (m == null) return@observe
            name.text = m.primaryName
            id.text = m.memberId
            details.text = buildString {
                append("Mobile: ").append(m.primaryMobile).append('\n')
                if (m.email.isNotBlank()) append("Email: ").append(m.email).append('\n')
                if (m.address.isNotBlank()) append("Address: ").append(m.address).append('\n')
                if (m.firstYear.isNotBlank()) append("First year: ").append(m.firstYear).append('\n')
                append("Status: ").append(m.status.ifBlank { "—" }).append('\n')
                if (m.notes.isNotBlank()) append("Notes: ").append(m.notes)
            }
        }

        ServiceLocator.membershipRepo.observe(memberId).observe(viewLifecycleOwner) { row ->
            if (row == null) {
                payments.text = "—"
                return@observe
            }
            val fees = RowMapper.Membership.parseFees(row.feesJson)
            payments.text = if (fees.isEmpty()) "—"
            else fees.entries.sortedByDescending { it.key }.joinToString("\n") { (fy, cell) ->
                "• $fy  ${cell.status.ifBlank { "—" }}  Rs.${cell.amount}  ${cell.date}  #${cell.receipt}"
            }
        }

        val canWrite = role?.canWrite == true
        val canDelete = role?.canDelete == true
        edit.visibility = if (canWrite) View.VISIBLE else View.GONE
        record.visibility = if (canWrite) View.VISIBLE else View.GONE
        delete.visibility = if (canDelete) View.VISIBLE else View.GONE

        edit.setOnClickListener {
            val b = Bundle().apply { putString("memberId", memberId) }
            findNavController().navigate(R.id.memberEditFragment, b)
        }
        record.setOnClickListener {
            val b = Bundle().apply { putString("memberId", memberId) }
            findNavController().navigate(R.id.recordPaymentFragment, b)
        }
        delete.setOnClickListener { confirmDelete(memberId) }
    }

    private fun confirmDelete(memberId: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.member_confirm_delete)
            .setPositiveButton(R.string.member_delete_ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
                    // Mark for deletion by writing "Deleted" status; the sheet
                    // keeps the row for audit. Full delete-row support lands
                    // in Phase B.
                    ServiceLocator.memberRepo.saveLocalEdit(m.copy(status = "Inactive"))
                    PushWorker.enqueue(requireContext())
                    findNavController().popBackStack()
                }
            }
            .setNegativeButton(R.string.member_delete_cancel, null)
            .show()
    }
}
