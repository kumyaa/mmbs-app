package org.mmbs.tracker.ui.member

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.data.local.entity.MemberEntity
import org.mmbs.tracker.sync.PushWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S-09 Add / Edit member. When arguments carry a memberId we edit, otherwise
 * we create a new row (with auto-assigned MM-XXXX id).
 */
class MemberEditFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_member_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val memberId = arguments?.getString("memberId")

        val title = view.findViewById<TextView>(R.id.title)
        val name = view.findViewById<EditText>(R.id.name)
        val mobile = view.findViewById<EditText>(R.id.mobile)
        val email = view.findViewById<EditText>(R.id.email)
        val address = view.findViewById<EditText>(R.id.address)
        val firstYear = view.findViewById<EditText>(R.id.firstYear)
        val status = view.findViewById<EditText>(R.id.status)
        val notes = view.findViewById<EditText>(R.id.notes)
        val errorText = view.findViewById<TextView>(R.id.errorText)
        val save = view.findViewById<Button>(R.id.saveButton)

        title.text = if (memberId == null) getString(R.string.member_edit_title_new)
        else getString(R.string.member_edit_title_edit)

        if (memberId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
                name.setText(m.primaryName)
                mobile.setText(m.primaryMobile)
                email.setText(m.email)
                address.setText(m.address)
                firstYear.setText(m.firstYear)
                status.setText(m.status)
                notes.setText(m.notes)
            }
        } else {
            status.setText("Active")
        }

        save.setOnClickListener {
            val primaryName = name.text.toString().trim()
            val primaryMobile = mobile.text.toString().trim()
            if (primaryName.isEmpty()) {
                show(errorText, getString(R.string.member_edit_error_name)); return@setOnClickListener
            }
            if (primaryMobile.isEmpty()) {
                show(errorText, getString(R.string.member_edit_error_mobile)); return@setOnClickListener
            }
            errorText.visibility = View.GONE

            viewLifecycleOwner.lifecycleScope.launch {
                val repo = ServiceLocator.memberRepo
                val existing = memberId?.let { repo.get(it) }
                val today = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date())
                val entity = (existing ?: MemberEntity(
                    memberId = repo.nextMemberId(),
                    regDate = today,
                    primaryName = primaryName,
                    primaryMobile = primaryMobile,
                    email = email.text.toString().trim(),
                    fm2Name = "", fm2Rel = "", fm2Mobile = "", fm2WaGroup = "",
                    fm3Name = "", fm3Rel = "", fm3Mobile = "", fm3WaGroup = "",
                    fm4Name = "", fm4Rel = "", fm4Mobile = "", fm4WaGroup = "",
                    address = address.text.toString().trim(),
                    firstYear = firstYear.text.toString().trim(),
                    status = status.text.toString().trim(),
                    totalFamilyMembers = "",
                    waGroupCount = "",
                    waValidation = "",
                    notes = notes.text.toString().trim(),
                )).copy(
                    primaryName = primaryName,
                    primaryMobile = primaryMobile,
                    email = email.text.toString().trim(),
                    address = address.text.toString().trim(),
                    firstYear = firstYear.text.toString().trim(),
                    status = status.text.toString().trim(),
                    notes = notes.text.toString().trim(),
                )
                repo.saveLocalEdit(entity)
                PushWorker.enqueue(requireContext())
                findNavController().popBackStack()
            }
        }
    }

    private fun show(label: TextView, msg: String) {
        label.text = msg
        label.visibility = View.VISIBLE
    }
}
