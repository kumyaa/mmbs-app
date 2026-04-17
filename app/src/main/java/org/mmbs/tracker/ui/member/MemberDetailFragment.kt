package org.mmbs.tracker.ui.member

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.core.util.WhatsApp
import org.mmbs.tracker.data.local.entity.MemberEntity
import org.mmbs.tracker.domain.fy.FinancialYear
import org.mmbs.tracker.domain.model.FyCell
import org.mmbs.tracker.sync.PushWorker
import org.mmbs.tracker.sync.RowMapper

/**
 * S-08 Member detail.
 *
 * Payments section:
 *   - Current FY card always visible, highlighted with orange border.
 *   - Older FYs collapsed behind a "Show older payments (N)" button.
 *   - Tapping any FY card opens Record Payment pre-loaded for that year.
 *
 * Family section:
 *   - One card per populated slot (fm2/fm3/fm4) with Call/WhatsApp/Edit.
 *   - First empty slot shows "+ Add family member" row (write role only).
 */
class MemberDetailFragment : Fragment() {

    /** Persists the expanded/collapsed state of older FYs across observer
     *  re-deliveries (push sync completing updates the row → observer fires). */
    private var olderExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_member_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val memberId = arguments?.getString("memberId").orEmpty()
        val role = ServiceLocator.currentRole
        val canWrite = role?.canWrite == true
        val canDelete = role?.canDelete == true

        val name = view.findViewById<TextView>(R.id.memberName)
        val id = view.findViewById<TextView>(R.id.memberId)
        val details = view.findViewById<TextView>(R.id.details)
        val paymentsContainer = view.findViewById<LinearLayout>(R.id.paymentsContainer)
        val paymentsEmpty = view.findViewById<TextView>(R.id.paymentsEmpty)
        val showOlderBtn = view.findViewById<Button>(R.id.showOlderButton)
        val olderContainer = view.findViewById<LinearLayout>(R.id.olderPaymentsContainer)
        val familyContainer = view.findViewById<LinearLayout>(R.id.familyContainer)
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
            renderFamily(familyContainer, m, canWrite)
        }

        ServiceLocator.membershipRepo.observe(memberId).observe(viewLifecycleOwner) { row ->
            val paid = row?.feesJson?.let { RowMapper.Membership.parseFees(it) } ?: emptyMap()
            val known = ServiceLocator.prefs.knownFyLabels
            val currentFy = FinancialYear.currentLabel()
            // Full ordered list (newest-first), deduped
            val merged = (paid.keys + currentFy + FinancialYear.nextLabel() + known)
                .mapNotNull { FinancialYear.normalize(it) }
                .distinct()
                .sortedByDescending { FinancialYear.startYear(it) ?: -1 }

            paymentsContainer.removeAllViews()
            olderContainer.removeAllViews()

            if (merged.isEmpty()) {
                paymentsEmpty.visibility = View.VISIBLE
                showOlderBtn.visibility = View.GONE
                olderContainer.visibility = View.GONE
                return@observe
            }
            paymentsEmpty.visibility = View.GONE

            // Current FY always rendered in the primary container
            paymentsContainer.addView(
                fyRow(currentFy, paid[currentFy], memberId, canWrite, isCurrent = true),
            )

            // Older FYs go into the collapsible container
            val olderFys = merged.filter { it != currentFy }
            if (olderFys.isEmpty()) {
                showOlderBtn.visibility = View.GONE
                olderContainer.visibility = View.GONE
            } else {
                showOlderBtn.visibility = View.VISIBLE
                // Preserve expanded state; update button text to match
                olderContainer.visibility = if (olderExpanded) View.VISIBLE else View.GONE
                updateShowOlderText(showOlderBtn, olderFys.size)

                for (fy in olderFys) {
                    olderContainer.addView(fyRow(fy, paid[fy], memberId, canWrite, isCurrent = false))
                }
                showOlderBtn.setOnClickListener {
                    olderExpanded = !olderExpanded
                    olderContainer.visibility = if (olderExpanded) View.VISIBLE else View.GONE
                    updateShowOlderText(showOlderBtn, olderFys.size)
                }
            }
        }

        parentFragmentManager.setFragmentResultListener(
            FamilyEditDialogFragment.REQUEST_KEY, viewLifecycleOwner,
        ) { _, bundle -> applyFamilyResult(memberId, bundle) }

        edit.visibility = if (canWrite) View.VISIBLE else View.GONE
        record.visibility = if (canWrite) View.VISIBLE else View.GONE
        delete.visibility = if (canDelete) View.VISIBLE else View.GONE

        edit.setOnClickListener {
            findNavController().navigate(
                R.id.memberEditFragment,
                Bundle().apply { putString("memberId", memberId) },
            )
        }
        record.setOnClickListener {
            findNavController().navigate(
                R.id.recordPaymentFragment,
                Bundle().apply { putString("memberId", memberId) },
            )
        }
        delete.setOnClickListener { confirmDelete(memberId) }
    }

    private fun updateShowOlderText(btn: Button, olderCount: Int) {
        btn.text = if (olderExpanded)
            getString(R.string.member_detail_hide_older)
        else
            getString(R.string.member_detail_show_older_fmt, olderCount)
    }

    // ---------- FY rows ----------

    private fun fyRow(
        fy: String,
        cell: FyCell?,
        memberId: String,
        canWrite: Boolean,
        isCurrent: Boolean,
    ): View {
        val ctx = requireContext()
        val bgRes = if (isCurrent) R.drawable.fy_row_bg_current else R.drawable.fy_row_bg
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, bgRes)
            val padV = resources.getDimensionPixelSize(R.dimen.row_padding_v)
            val padH = resources.getDimensionPixelSize(R.dimen.row_padding_h)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = resources.getDimensionPixelSize(R.dimen.row_gap) }
            if (canWrite) {
                val tv = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                foreground = ContextCompat.getDrawable(ctx, tv.resourceId)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    findNavController().navigate(
                        R.id.recordPaymentFragment,
                        Bundle().apply {
                            putString("memberId", memberId)
                            putString("fy", fy)
                        },
                    )
                }
            }
        }
        container.addView(TextView(ctx).apply {
            text = fy
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_orange_brown))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        container.addView(TextView(ctx).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
            text = if (cell == null || cell.isBlank) {
                getString(R.string.member_detail_fy_empty)
            } else {
                buildString {
                    append(cell.status.ifBlank { "—" })
                    if (cell.amount.isNotBlank()) append("  ·  Rs.").append(cell.amount)
                    if (cell.date.isNotBlank()) append("  ·  ").append(cell.date)
                    if (cell.receipt.isNotBlank()) append("  ·  #").append(cell.receipt)
                }
            }
        })
        return container
    }

    // ---------- Family rows ----------

    private fun renderFamily(container: LinearLayout, m: MemberEntity, canWrite: Boolean) {
        container.removeAllViews()
        val slots = listOf(
            FamilySlot(2, m.fm2Name, m.fm2Rel, m.fm2Mobile, m.fm2WaGroup),
            FamilySlot(3, m.fm3Name, m.fm3Rel, m.fm3Mobile, m.fm3WaGroup),
            FamilySlot(4, m.fm4Name, m.fm4Rel, m.fm4Mobile, m.fm4WaGroup),
        )
        for (slot in slots.filter { it.name.isNotBlank() }) {
            container.addView(familyCard(slot, canWrite))
        }
        if (canWrite) {
            slots.firstOrNull { it.name.isBlank() }?.let {
                container.addView(familyAddRow(it.slot))
            }
        }
    }

    private fun familyCard(slot: FamilySlot, canWrite: Boolean): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.fy_row_bg)
            val padV = resources.getDimensionPixelSize(R.dimen.row_padding_v)
            val padH = resources.getDimensionPixelSize(R.dimen.row_padding_h)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = resources.getDimensionPixelSize(R.dimen.row_gap) }
        }
        card.addView(TextView(ctx).apply {
            text = buildString {
                append(slot.name)
                if (slot.relation.isNotBlank()) append("  ·  ").append(slot.relation)
            }
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_orange_brown))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(ctx).apply {
            text = slot.mobile.ifBlank { getString(R.string.member_detail_family_no_mobile) }
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
        })
        val inGroup = slot.waGroup.trim().equals("yes", ignoreCase = true)
        card.addView(TextView(ctx).apply {
            text = if (inGroup) getString(R.string.member_detail_family_wa_in)
            else getString(R.string.member_detail_family_wa_out)
            textSize = 12f
            setTextColor(
                ContextCompat.getColor(
                    ctx, if (inGroup) R.color.status_synced else R.color.brand_grey,
                ),
            )
        })
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = resources.getDimensionPixelSize(R.dimen.row_gap) }
            addView(smallButton(R.string.member_detail_family_call) { placeCall(slot.mobile) })
            addView(smallButton(R.string.member_detail_family_whatsapp) { openWhatsApp(slot.mobile, slot.name) })
            if (canWrite) addView(smallButton(R.string.member_detail_family_edit) { showFamilyDialog(slot) })
        })
        return card
    }

    private fun familyAddRow(slot: Int): View {
        val ctx = requireContext()
        return TextView(ctx).apply {
            text = getString(R.string.member_detail_family_add)
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_orange_brown))
            gravity = Gravity.CENTER
            val padV = resources.getDimensionPixelSize(R.dimen.row_padding_v)
            val padH = resources.getDimensionPixelSize(R.dimen.row_padding_h)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = resources.getDimensionPixelSize(R.dimen.row_gap) }
            background = ContextCompat.getDrawable(ctx, R.drawable.fy_row_bg)
            val tv = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            foreground = ContextCompat.getDrawable(ctx, tv.resourceId)
            isClickable = true; isFocusable = true
            setOnClickListener { showFamilyDialog(FamilySlot(slot, "", "", "", "")) }
        }
    }

    private fun smallButton(textRes: Int, onClick: () -> Unit): Button =
        Button(requireContext()).apply {
            text = getString(textRes)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginEnd = resources.getDimensionPixelSize(R.dimen.row_gap) }
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }

    private fun placeCall(phone: String) {
        val digits = phone.filter { it.isDigit() || it == '+' }
        if (digits.isBlank()) {
            Toast.makeText(requireContext(), R.string.member_detail_family_no_mobile, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.member_detail_family_no_mobile, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsApp(phone: String, name: String) {
        if (phone.isBlank()) {
            Toast.makeText(requireContext(), R.string.member_detail_family_no_mobile, Toast.LENGTH_SHORT).show()
            return
        }
        WhatsApp.send(requireContext(), phone, "Namaskar ${name.trim()}".trim())
    }

    private fun showFamilyDialog(slot: FamilySlot) {
        FamilyEditDialogFragment.newInstance(
            slot = slot.slot,
            name = slot.name,
            relation = slot.relation,
            mobile = slot.mobile,
            waGroup = slot.waGroup,
        ).show(parentFragmentManager, "family_edit")
    }

    private fun applyFamilyResult(memberId: String, bundle: Bundle) {
        val slot = bundle.getInt(FamilyEditDialogFragment.RESULT_SLOT)
        val cleared = bundle.getBoolean(FamilyEditDialogFragment.RESULT_CLEARED)
        val name = if (cleared) "" else bundle.getString(FamilyEditDialogFragment.RESULT_NAME).orEmpty()
        val rel = if (cleared) "" else bundle.getString(FamilyEditDialogFragment.RESULT_RELATION).orEmpty()
        val mobile = if (cleared) "" else bundle.getString(FamilyEditDialogFragment.RESULT_MOBILE).orEmpty()
        val wa = if (cleared) "" else bundle.getString(FamilyEditDialogFragment.RESULT_WA_GROUP).orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
            val updated = when (slot) {
                2 -> m.copy(fm2Name = name, fm2Rel = rel, fm2Mobile = mobile, fm2WaGroup = wa)
                3 -> m.copy(fm3Name = name, fm3Rel = rel, fm3Mobile = mobile, fm3WaGroup = wa)
                4 -> m.copy(fm4Name = name, fm4Rel = rel, fm4Mobile = mobile, fm4WaGroup = wa)
                else -> return@launch
            }
            ServiceLocator.memberRepo.saveLocalEdit(updated)
            PushWorker.enqueue(requireContext())
        }
    }

    private data class FamilySlot(
        val slot: Int,
        val name: String,
        val relation: String,
        val mobile: String,
        val waGroup: String,
    )

    private fun confirmDelete(memberId: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.member_confirm_delete)
            .setPositiveButton(R.string.member_delete_ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
                    ServiceLocator.memberRepo.saveLocalEdit(m.copy(status = "Inactive"))
                    PushWorker.enqueue(requireContext())
                    findNavController().popBackStack()
                }
            }
            .setNegativeButton(R.string.member_delete_cancel, null)
            .show()
    }
}
