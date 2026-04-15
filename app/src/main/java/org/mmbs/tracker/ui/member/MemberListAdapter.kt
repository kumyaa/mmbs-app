package org.mmbs.tracker.ui.member

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.mmbs.tracker.R
import org.mmbs.tracker.data.local.SyncState
import org.mmbs.tracker.data.local.entity.MemberEntity

class MemberListAdapter(
    private val onClick: (MemberEntity) -> Unit,
) : ListAdapter<MemberEntity, MemberListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = getItem(position)
        holder.bind(m)
        holder.itemView.setOnClickListener { onClick(m) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val name = v.findViewById<TextView>(R.id.name)
        private val badge = v.findViewById<TextView>(R.id.statusBadge)
        private val subtitle = v.findViewById<TextView>(R.id.subtitle)
        private val dot = v.findViewById<View>(R.id.syncDot)

        fun bind(m: MemberEntity) {
            name.text = m.primaryName
            badge.text = m.status.ifBlank { "—" }
            subtitle.text = buildString {
                append(m.memberId)
                if (m.primaryMobile.isNotBlank()) append("  ·  ").append(m.primaryMobile)
            }
            val colorRes = when (m.syncStatus) {
                SyncState.SYNCED -> R.color.status_synced
                SyncState.PENDING -> R.color.status_pending
                SyncState.FAILED -> R.color.status_failed
            }
            dot.setBackgroundColor(dot.context.getColor(colorRes))
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MemberEntity>() {
            override fun areItemsTheSame(old: MemberEntity, new: MemberEntity) =
                old.memberId == new.memberId
            override fun areContentsTheSame(old: MemberEntity, new: MemberEntity) =
                old == new
        }
    }
}
