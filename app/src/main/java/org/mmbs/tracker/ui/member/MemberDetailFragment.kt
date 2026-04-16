package org.mmbs.tracker.ui.member

import android.app.AlertDialog
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.domain.fy.FinancialYear
import org.mmbs.tracker.domain.model.FyCell
import org.mmbs.tracker.sync.PushWorker
import org.mmbs.tracker.sync.RowMapper

/**
 * S-08 Member detail. Shows the member's fields + their Membership Tracker
 * payments as one clickable row per FY. Tapping an FY row opens
 * RecordPaymentFragment pre-loaded with that year's existing values — so the
 * same screen serves as "record" AND "edit for FY X" without a separate edit
 * page. The row list is the union of (FYs with cells) ∪ {current FY, next
 * FY}; the latter two cover the Jan-Mar advance-payment window (members
 * paying for the upcoming FY to stretch membership to ~15 months).
 *
 * Treasurer sees Delete; anyone with write access sees Edit and
 * Record-Payment buttons. Auditor sees the page read-only; FY rows are
 * still listed but not clickable.
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
        val paymentsContainer = view.findViewById<LinearLayout>(R.id.paymentsContainer)
        val paymentsEmpty = view.findViewById<TextView>(R.id.paymentsEmpty)
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

        val canWrite = role?.canWrite == true
        val canDelete = role?.canDelete == true

        ServiceLocator.membershipRepo.observe(memberId).observe(viewLifecycleOwner) { row ->
            val paid = row?.feesJson?.let { RowMapper.Membership.parseFees(it) } ?: emptyMap()
            // Union: FYs with recorded cells + current + next. Deduped,
            // sorted newest-first so recent years are at the top.
            val known = ServiceLocator.prefs.knownFyLabels
            val merged = (paid.keys + FinancialYear.currentLabel() + FinancialYear.nextLabel() + known)
                .mapNotNull { FinancialYear.normalize(it) }
                .distinct()
                .sortedByDescending { FinancialYear.startYear(it) ?: -1 }

            paymentsContainer.removeAllViews()
            if (merged.isEmpty()) {
                paymentsEmpty.visibility = View.VISIBLE
            } else {
                paymentsEmpty.visibility = View.GONE
                for (fy in merged) {
                    paymentsContainer.addView(fyRow(fy, paid[fy], memberId, canWrite))
                }
            }
        }

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

    /**
     * Build a single clickable row for one FY. If [cell] is null the row
     * reads "Not recorded — tap to add"; otherwise it shows the flattened
     * status/amount/date/receipt. Tapping any row (when [canWrite]) routes
     * to RecordPaymentFragment with the fy arg so the dropdown pre-selects
     * and the inputs pre-fill.
     */
    private fun fyRow(fy: String, cell: FyCell?, memberId: String, canWrite: Boolean): View {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.fy_row_bg)
            val padV = resources.getDimensionPixelSize(R.dimen.row_padding_v)
            val padH = resources.getDimensionPixelSize(R.dimen.row_padding_h)
            setPadding(padH, padV, padH, padV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = resources.getDimensionPixelSize(R.dimen.row_gap)
            layoutParams = lp
            if (canWrite) {
                // Standard Material ripple on touch; noop for Auditor role.
                val tv = TypedValue()
                ctx.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true,
                )
                foreground = ContextCompat.getDrawable(ctx, tv.resourceId)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val b = Bundle().apply {
                        putString("memberId", memberId)
                        putString("fy", fy)
                    }
                    findNavController().navigate(R.id.recordPaymentFragment, b)
                }
            }
        }
        val header = TextView(ctx).apply {
            text = fy
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_orange_brown))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val body = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
            text = if (cell == null) {
                getString(R.string.member_detail_fy_empty)
            } else {
                buildString {
                    append(cell.status.ifBlank { "—" })
                    if (cell.amount.isNotBlank()) append("  ·  Rs.").append(cell.amount)
                    if (cell.date.isNotBlank()) append("  ·  ").append(cell.date)
                    if (cell.receipt.isNotBlank()) append("  ·  #").append(cell.receipt)
                }
            }
        }
        container.addView(header)
        container.addView(body)
        return container
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
