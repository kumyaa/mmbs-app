package org.mmbs.tracker.ui.conflict

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.sync.ConflictRow
import org.mmbs.tracker.sync.PushWorker
import org.mmbs.tracker.sync.RowMapper
import org.mmbs.tracker.sync.SheetSpec

/**
 * S-04 Conflict resolution. After SyncEngine reports `hasConflicts`, this
 * fragment re-runs the pull just to gather the conflict rows again, lays out
 * one card per row with App/Sheet toggles per field, then applies the
 * decisions and re-syncs.
 *
 * Phase A keeps the resolver simple: per-row choice, not per-field. A future
 * iteration can split per-field if users complain.
 */
class ConflictResolutionFragment : Fragment() {

    /** Per-row decision: true = take Sheet (overwrite local), false = take App (push local). */
    private val decisions = mutableMapOf<String, Boolean>()
    private var conflicts: List<ConflictRow> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_conflict, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = view.findViewById<LinearLayout>(R.id.container)
        val apply = view.findViewById<Button>(R.id.applyButton)

        viewLifecycleOwner.lifecycleScope.launch {
            val res = ServiceLocator.syncEngine.syncNow()
            conflicts = res.conflicts
            renderConflicts(container)
        }

        apply.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                applyDecisions()
                ServiceLocator.syncEngine.syncNow()
                PushWorker.enqueue(requireContext())
                findNavController().navigate(
                    R.id.homeFragment,
                    null,
                    androidx.navigation.navOptions {
                        popUpTo(R.id.homeFragment) { inclusive = true }
                    }
                )
            }
        }
    }

    private fun renderConflicts(container: LinearLayout) {
        container.removeAllViews()
        if (conflicts.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.conflict_none)
                setPadding(16, 16, 16, 16)
            })
            return
        }
        conflicts.forEach { row ->
            container.addView(buildCard(row))
        }
    }

    private fun buildCard(row: ConflictRow): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ctx.getColor(R.color.brand_white))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.setMargins(8, 8, 8, 8)
            layoutParams = lp
        }

        card.addView(TextView(ctx).apply {
            text = getString(R.string.conflict_row_fmt, row.table, row.sheetRowIndex ?: 0)
            textSize = 14f
            setTextColor(ctx.getColor(R.color.brand_orange_brown))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(ctx).apply {
            text = row.rowKey
            textSize = 12f
            setTextColor(ctx.getColor(R.color.brand_grey))
        })
        row.diffs.take(8).forEach { d ->
            card.addView(TextView(ctx).apply {
                text = "${d.column}:  app=\"${d.localValue}\"  sheet=\"${d.remoteValue}\""
                textSize = 12f
                setPadding(0, 6, 0, 0)
            })
        }
        if (row.diffs.size > 8) {
            card.addView(TextView(ctx).apply {
                text = "… and ${row.diffs.size - 8} more fields"
                textSize = 11f
                setTextColor(ctx.getColor(R.color.brand_grey))
            })
        }

        val toggleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.setMargins(0, 12, 0, 0)
            layoutParams = lp
        }
        val key = "${row.table}|${row.rowKey}"
        val appBtn = Button(ctx).apply { text = getString(R.string.conflict_use_app) }
        val sheetBtn = Button(ctx).apply { text = getString(R.string.conflict_use_sheet) }
        val updateButtons = {
            val pickedSheet = decisions[key]
            appBtn.alpha = if (pickedSheet == false) 1f else 0.5f
            sheetBtn.alpha = if (pickedSheet == true) 1f else 0.5f
        }
        appBtn.setOnClickListener { decisions[key] = false; updateButtons() }
        sheetBtn.setOnClickListener { decisions[key] = true; updateButtons() }
        updateButtons()
        toggleRow.addView(appBtn)
        toggleRow.addView(sheetBtn)
        card.addView(toggleRow)

        return card
    }

    private suspend fun applyDecisions() {
        conflicts.forEach { row ->
            val key = "${row.table}|${row.rowKey}"
            val takeSheet = decisions[key] ?: true // default to sheet if user didn't pick
            when (row.table) {
                "members" -> resolveMember(row, takeSheet)
                "transactions" -> resolveTransaction(row, takeSheet)
                "membership_rows" -> resolveMembership(row, takeSheet)
            }
        }
    }

    private suspend fun resolveMember(row: ConflictRow, takeSheet: Boolean) {
        val repo = ServiceLocator.memberRepo
        val m = repo.get(row.rowKey) ?: return
        if (takeSheet) {
            // Re-pull will overwrite. Mark SYNCED so push doesn't clobber it.
            val cleared = m.copy(
                syncStatus = SyncState.SYNCED,
                pushError = null,
                lastSyncedSnapshot = "", // forces next pull to write a fresh snapshot
            )
            repo.upsert(cleared)
        } else {
            // Pretend snapshot matches remote so the next push wins outright.
            val rebuilt = RowMapper.rowToSnapshot(
                row.diffs.fold(RowMapper.Members.toRow(m).toMutableList()) { acc, d ->
                    val idx = colIndexFromLetter(d.column)
                    if (idx in acc.indices) acc[idx] = d.remoteValue
                    acc
                },
                SheetSpec.MEMBERS_COL_COUNT,
            )
            repo.upsert(m.copy(lastSyncedSnapshot = rebuilt))
        }
    }

    private suspend fun resolveTransaction(row: ConflictRow, takeSheet: Boolean) {
        val repo = ServiceLocator.transactionRepo
        val t = repo.get(row.rowKey) ?: return
        if (takeSheet) {
            repo.upsert(t.copy(syncStatus = SyncState.SYNCED, pushError = null, lastSyncedSnapshot = ""))
        } else {
            val rebuilt = RowMapper.rowToSnapshot(
                row.diffs.fold(RowMapper.Transactions.toRow(t).toMutableList()) { acc, d ->
                    val idx = colIndexFromLetter(d.column)
                    if (idx in acc.indices) acc[idx] = d.remoteValue
                    acc
                },
                SheetSpec.TRANSACTIONS_COL_COUNT,
            )
            repo.upsert(t.copy(lastSyncedSnapshot = rebuilt))
        }
    }

    private suspend fun resolveMembership(row: ConflictRow, takeSheet: Boolean) {
        val repo = ServiceLocator.membershipRepo
        val m = repo.get(row.rowKey) ?: return
        if (takeSheet) {
            repo.upsert(m.copy(syncStatus = SyncState.SYNCED, pushError = null, lastSyncedSnapshot = ""))
        } else {
            // For membership rows, snapshot is the feesJson — set it to current
            // remote (encoded from diffs) so the local feesJson wins on push.
            repo.upsert(m.copy(lastSyncedSnapshot = m.feesJson))
        }
    }

    /** "A"->0, "Z"->25, "AA"->26. */
    private fun colIndexFromLetter(s: String): Int {
        var n = 0
        s.uppercase().forEach { ch ->
            if (ch !in 'A'..'Z') return -1
            n = n * 26 + (ch - 'A' + 1)
        }
        return n - 1
    }
}
