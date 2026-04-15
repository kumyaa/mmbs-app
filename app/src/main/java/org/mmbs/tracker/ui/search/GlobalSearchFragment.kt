package org.mmbs.tracker.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.ui.member.MemberListAdapter

/** S-06 Global search — full-text over members' name/id/mobile/email/TXN id. */
class GlobalSearchFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val input = view.findViewById<EditText>(R.id.searchInput)
        val empty = view.findViewById<TextView>(R.id.emptyText)
        val list = view.findViewById<RecyclerView>(R.id.results)

        val adapter = MemberListAdapter { m ->
            val b = Bundle().apply { putString("memberId", m.memberId) }
            findNavController().navigate(R.id.memberDetailFragment, b)
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        var all = emptyList<org.mmbs.tracker.data.local.entity.MemberEntity>()

        ServiceLocator.memberRepo.observeAll().observe(viewLifecycleOwner) {
            all = it
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                val filtered = if (q.isEmpty()) emptyList() else all.filter { m ->
                    listOf(
                        m.primaryName, m.memberId, m.primaryMobile, m.email,
                        m.fm2Name, m.fm3Name, m.fm4Name,
                    ).any { it.contains(q, ignoreCase = true) }
                }
                adapter.submitList(filtered)
                empty.visibility = if (q.isNotEmpty() && filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        })
    }
}
