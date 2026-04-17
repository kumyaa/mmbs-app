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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.data.local.entity.MemberEntity
import org.mmbs.tracker.domain.fy.FinancialYear
import org.mmbs.tracker.sync.PushWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S-09 Add / Edit member. When arguments carry a memberId we edit, otherwise
 * we create a new row (with auto-assigned MM-XXXX id).
 *
 * Status and First Year are Spinners so the user picks from validated lists
 * rather than free-typing. Status options: Active / Inactive / Suspended.
 * First Year covers FY 2019-20 (earliest MMBS records) through next FY,
 * sorted newest-first so a new enrolment is at the top.
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
        val firstYearSpinner = view.findViewById<Spinner>(R.id.firstYear)
        val statusSpinner = view.findViewById<Spinner>(R.id.status)
        val notes = view.findViewById<EditText>(R.id.notes)
        val errorText = view.findViewById<TextView>(R.id.errorText)
        val save = view.findViewById<Button>(R.id.saveButton)

        title.text = if (memberId == null) getString(R.string.member_edit_title_new)
        else getString(R.string.member_edit_title_edit)

        // Status spinner
        bindSpinner(statusSpinner, STATUS_OPTIONS, "Active")

        // First-year spinner: newest FY at the top, oldest (2019-20) at the
        // bottom. This way adding a new enrolment doesn't require scrolling.
        val fyOptions = buildFyOptions()
        bindSpinner(firstYearSpinner, fyOptions, FinancialYear.currentLabel())

        if (memberId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
                name.setText(m.primaryName)
                mobile.setText(m.primaryMobile)
                email.setText(m.email)
                address.setText(m.address)
                notes.setText(m.notes)
                selectOrAdd(statusSpinner, STATUS_OPTIONS, m.status)
                selectOrAdd(firstYearSpinner, fyOptions, m.firstYear)
            }
        }

        save.setOnClickListener {
            val primaryName = name.text.toString().trim()
            val primaryMobile = mobile.text.toString().trim()
            if (primaryName.isEmpty()) {
                show(errorText, getString(R.string.member_edit_error_name))
                return@setOnClickListener
            }
            if (primaryMobile.isEmpty()) {
                show(errorText, getString(R.string.member_edit_error_mobile))
                return@setOnClickListener
            }
            errorText.visibility = View.GONE

            viewLifecycleOwner.lifecycleScope.launch {
                val repo = ServiceLocator.memberRepo
                val existing = memberId?.let { repo.get(it) }
                val today = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date())
                val selectedStatus = statusSpinner.selectedItem?.toString().orEmpty()
                val selectedFirstYear = firstYearSpinner.selectedItem?.toString().orEmpty()
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
                    firstYear = selectedFirstYear,
                    status = selectedStatus,
                    totalFamilyMembers = "",
                    waGroupCount = "",
                    waValidation = "",
                    notes = notes.text.toString().trim(),
                )).copy(
                    primaryName = primaryName,
                    primaryMobile = primaryMobile,
                    email = email.text.toString().trim(),
                    address = address.text.toString().trim(),
                    firstYear = selectedFirstYear,
                    status = selectedStatus,
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

    companion object {
        val STATUS_OPTIONS = listOf("Active", "Inactive", "Suspended")

        /**
         * FY options from 2019-20 (earliest MMBS records) up to next FY,
         * returned newest-first so new enrolments don't require scrolling.
         */
        fun buildFyOptions(): List<String> {
            val nextStart = FinancialYear.startYear(FinancialYear.nextLabel()) ?: 2026
            return (2019..nextStart).map { FinancialYear.label(it) }.reversed()
        }

        /**
         * Bind [spinner] with [options] and default-select [defaultValue].
         * If [defaultValue] is not in [options], the first item is selected.
         */
        fun bindSpinner(spinner: Spinner, options: List<String>, defaultValue: String) {
            val ctx = spinner.context
            val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, options)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            spinner.adapter = adapter
            val idx = options.indexOf(defaultValue).takeIf { it >= 0 } ?: 0
            spinner.setSelection(idx)
        }

        /**
         * Pre-select [value] in [spinner]. If [value] is not in [currentOptions]
         * (e.g. legacy data), build a new adapter that prepends it so the
         * existing value is not silently overwritten.
         */
        fun selectOrAdd(spinner: Spinner, currentOptions: List<String>, value: String) {
            if (value.isBlank()) return
            if (value in currentOptions) {
                spinner.setSelection(currentOptions.indexOf(value))
            } else {
                val extended = listOf(value) + currentOptions
                bindSpinner(spinner, extended, value)
            }
        }
    }
}
