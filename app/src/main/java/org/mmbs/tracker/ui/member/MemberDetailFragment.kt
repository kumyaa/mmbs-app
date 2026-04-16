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
 * S-08 Member detail. Shows:
 *   1. Primary member fields
 *   2. Clickable FY payment history (tap → Record Payment pre-filled)
 *   3. Family members (up to 3 slots) with Call/WhatsApp/Edit per slot and
 *      a "+ Add family member" row if any slot is free
 *
 * Treasurer sees Delete + full write access; Auditor sees everything
 * read-only (no ripples, no click listeners, edit/record/delete buttons
 * hidden). FY rows and Family Edit are gated on [canWrite].
 */
class MemberDetailFragment : Fragment() {

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

        // Family dialog returns via FragmentResult — bounce it into the
        // member repo on the main thread.
        parentFragmentManager.setFragmentResultListener(
            FamilyEditDialogFragment.REQUEST_KEY, viewLifecycleOwner,
        ) { _, bundle ->
            applyFamilyResult(memberId, bundle)
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

    // ---------- FY rows ----------

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
        container.addView(TextView(ctx).apply {
            text = fy
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_orange_brown))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        container.addView(TextView(ctx).apply {
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
        val populated = slots.filter { it.name.isNotBlank() }
        for (slot in populated) {
            container.addView(familyCard(slot, canWrite))
        }
        if (canWrite) {
            val firstEmpty = slots.firstOrNull { it.name.isBlank() }
            if (firstEmpty != null) {
                container.addView(familyAddRow(firstEmpty.slot))
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
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = resources.getDimensionPixelSize(R.dimen.row_gap)
            layoutParams = lp
        }
        // Line 1: Name + optional relation in grey
        card.addView(TextView(ctx).apply {
            text = buildString {
                append(slot.name)
                if (slot.relation.isNotBlank()) append("  ·  ").append(slot.relation)
            }
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_orange_brown))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        // Line 2: mobile
        card.addView(TextView(ctx).apply {
            text = slot.mobile.ifBlank { getString(R.string.member_detail_family_no_mobile) }
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
        })
        // Line 3: WA-group indicator
        val inGroup = slot.waGroup.trim().equals("yes", ignoreCase = true)
        card.addView(TextView(ctx).apply {
            text = if (inGroup) getString(R.string.member_detail_family_wa_in)
                else getString(R.string.member_detail_family_wa_out)
            textSize = 12f
            setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (inGroup) R.color.status_synced else R.color.brand_grey,
                ),
            )
        })
        // Button row: Call, WhatsApp, Edit
        val buttonRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = resources.getDimensionPixelSize(R.dimen.row_gap)
            layoutParams = lp
        }
        buttonRow.addView(smallButton(R.string.member_detail_family_call) {
            placeCall(slot.mobile)
        })
        buttonRow.addView(smallButton(R.string.member_detail_family_whatsapp) {
            openWhatsApp(slot.mobile, slot.name)
        })
        if (canWrite) {
            buttonRow.addView(smallButton(R.string.member_detail_family_edit) {
                showFamilyDialog(slot)
            })
        }
        card.addView(buttonRow)
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
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = resources.getDimensionPixelSize(R.dimen.row_gap)
            layoutParams = lp
            background = ContextCompat.getDrawable(ctx, R.drawable.fy_row_bg)
            val tv = TypedValue()
            ctx.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true,
            )
            foreground = ContextCompat.getDrawable(ctx, tv.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showFamilyDialog(FamilySlot(slot, "", "", "", ""))
            }
        }
    }

    private fun smallButton(textRes: Int, onClick: () -> Unit): Button {
        val ctx = requireContext()
        return Button(ctx).apply {
            text = getString(textRes)
            textSize = 12f
            val lp = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
            )
            lp.marginEnd = resources.getDimensionPixelSize(R.dimen.row_gap)
            layoutParams = lp
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun placeCall(phone: String) {
        val digits = phone.filter { it.isDigit() || it == '+' }
        if (digits.isBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.member_detail_family_no_mobile,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        // ACTION_DIAL opens the dialer pre-filled; no CALL_PHONE permission
        // needed and the user always taps the green button themselves.
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                requireContext(),
                R.string.member_detail_family_no_mobile,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun openWhatsApp(phone: String, name: String) {
        if (phone.isBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.member_detail_family_no_mobile,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val greeting = "Namaskar ${name.ifBlank { "" }}".trim()
        WhatsApp.send(requireContext(), phone, greeting)
    }

    private fun showFamilyDialog(slot: FamilySlot) {
        FamilyEditDialogFragment
            .newInstance(
                slot = slot.slot,
                name = slot.name,
                relation = slot.relation,
                mobile = slot.mobile,
                waGroup = slot.waGroup,
            )
            .show(parentFragmentManager, "family_edit")
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
                    // Mark for deletion by writing "Inactive" status; the sheet
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
