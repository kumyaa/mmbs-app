package org.mmbs.tracker.ui.member

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.data.local.entity.MemberEntity

class MemberListFragment : Fragment() {

    private var statusFilter: String? = null
    private var query: String = ""
    private var all: List<MemberEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_member_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list = view.findViewById<RecyclerView>(R.id.list)
        val search = view.findViewById<EditText>(R.id.searchInput)
        val count = view.findViewById<TextView>(R.id.countLabel)
        val addBtn = view.findViewById<Button>(R.id.addButton)

        val adapter = MemberListAdapter { member ->
            val bundle = Bundle().apply { putString("memberId", member.memberId) }
            findNavController().navigate(R.id.memberDetailFragment, bundle)
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        fun applyFilter() {
            val filtered = all.filter { m ->
                val matchesStatus =
                    statusFilter == null || m.status.equals(statusFilter, ignoreCase = true)
                val matchesQuery = query.isEmpty() ||
                    listOf(
                        m.primaryName, m.memberId, m.primaryMobile,
                        m.fm2Name, m.fm3Name, m.fm4Name, m.email,
                    ).any { it.contains(query, ignoreCase = true) }
                matchesStatus && matchesQuery
            }
            adapter.submitList(filtered)
            count.text = getString(R.string.members_count_fmt, filtered.size)
        }

        ServiceLocator.memberRepo.observeAll().observe(viewLifecycleOwner) {
            all = it
            applyFilter()
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
        })

        view.findViewById<Button>(R.id.filterAll).setOnClickListener { statusFilter = null; applyFilter() }
        view.findViewById<Button>(R.id.filterActive).setOnClickListener { statusFilter = "Active"; applyFilter() }
        view.findViewById<Button>(R.id.filterInactive).setOnClickListener { statusFilter = "Inactive"; applyFilter() }
        view.findViewById<Button>(R.id.filterSuspended).setOnClickListener { statusFilter = "Suspended"; applyFilter() }

        val canWrite = ServiceLocator.currentRole?.canWrite == true
        addBtn.visibility = if (canWrite) View.VISIBLE else View.GONE
        addBtn.setOnClickListener {
            findNavController().navigate(R.id.memberEditFragment)
        }
    }
}
