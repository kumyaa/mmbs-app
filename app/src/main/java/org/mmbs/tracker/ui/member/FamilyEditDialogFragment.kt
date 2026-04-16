package org.mmbs.tracker.ui.member

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import org.mmbs.tracker.R

/**
 * Inline edit dialog for a single family slot (fm2 / fm3 / fm4 on the
 * Members sheet row). Used by Member Detail for both "Add family member"
 * (blank slot) and "Edit" (populated slot) flows.
 *
 * The dialog returns its result via FragmentResult. The host fragment
 * handles the actual Room + sheet write so this dialog stays
 * ServiceLocator-free and easy to test.
 *
 * Result bundle keys:
 *   - "slot"     : Int (2, 3, or 4)
 *   - "cleared"  : Boolean — user tapped "Clear slot" → wipe all 4 fields
 *   - "name"     : String
 *   - "relation" : String
 *   - "mobile"   : String
 *   - "waGroup"  : String ("Yes" or "No")
 */
class FamilyEditDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val slot = args.getInt(ARG_SLOT)
        val initialName = args.getString(ARG_NAME).orEmpty()
        val initialRelation = args.getString(ARG_RELATION).orEmpty()
        val initialMobile = args.getString(ARG_MOBILE).orEmpty()
        val initialWaGroup = args.getString(ARG_WA_GROUP).orEmpty()

        val view = layoutInflater.inflate(R.layout.dialog_family_edit, null)
        val nameEt = view.findViewById<EditText>(R.id.familyName)
        val relEt = view.findViewById<EditText>(R.id.familyRelation)
        val mobEt = view.findViewById<EditText>(R.id.familyMobile)
        val waSw = view.findViewById<SwitchCompat>(R.id.familyWaSwitch)
        val errorTv = view.findViewById<TextView>(R.id.familyError)

        nameEt.setText(initialName)
        relEt.setText(initialRelation)
        mobEt.setText(initialMobile)
        waSw.isChecked = initialWaGroup.trim().equals("yes", ignoreCase = true)

        // Build with setView + neutral/positive/negative buttons. Positive
        // click behaviour is overridden after show() so we can validate name
        // WITHOUT auto-dismissing the dialog on invalid input.
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.family_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.family_dialog_save, null)
            .setNegativeButton(R.string.family_dialog_cancel, null)
            .setNeutralButton(R.string.family_dialog_clear, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val name = nameEt.text.toString().trim()
                if (name.isEmpty()) {
                    errorTv.text = getString(R.string.family_dialog_name_required)
                    errorTv.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val result = Bundle().apply {
                    putInt(RESULT_SLOT, slot)
                    putBoolean(RESULT_CLEARED, false)
                    putString(RESULT_NAME, name)
                    putString(RESULT_RELATION, relEt.text.toString().trim())
                    putString(RESULT_MOBILE, mobEt.text.toString().trim())
                    putString(RESULT_WA_GROUP, if (waSw.isChecked) "Yes" else "No")
                }
                setFragmentResult(REQUEST_KEY, result)
                dialog.dismiss()
            }
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            neutral.setOnClickListener {
                val result = Bundle().apply {
                    putInt(RESULT_SLOT, slot)
                    putBoolean(RESULT_CLEARED, true)
                }
                setFragmentResult(REQUEST_KEY, result)
                dialog.dismiss()
            }
        }
        return dialog
    }

    companion object {
        const val REQUEST_KEY = "family_edit_result"

        const val RESULT_SLOT = "slot"
        const val RESULT_CLEARED = "cleared"
        const val RESULT_NAME = "name"
        const val RESULT_RELATION = "relation"
        const val RESULT_MOBILE = "mobile"
        const val RESULT_WA_GROUP = "waGroup"

        private const val ARG_SLOT = "slot"
        private const val ARG_NAME = "name"
        private const val ARG_RELATION = "relation"
        private const val ARG_MOBILE = "mobile"
        private const val ARG_WA_GROUP = "waGroup"

        fun newInstance(
            slot: Int,
            name: String,
            relation: String,
            mobile: String,
            waGroup: String,
        ): FamilyEditDialogFragment = FamilyEditDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_SLOT, slot)
                putString(ARG_NAME, name)
                putString(ARG_RELATION, relation)
                putString(ARG_MOBILE, mobile)
                putString(ARG_WA_GROUP, waGroup)
            }
        }
    }
}
