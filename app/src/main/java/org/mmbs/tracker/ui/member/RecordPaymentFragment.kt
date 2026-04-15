package org.mmbs.tracker.ui.member

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.mmbs.tracker.R
import org.mmbs.tracker.ServiceLocator
import org.mmbs.tracker.core.util.WhatsApp
import org.mmbs.tracker.domain.fy.FinancialYear
import org.mmbs.tracker.domain.model.FyCell
import org.mmbs.tracker.sync.PushWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S-10 Record payment. Writes one FY cell on the member's Membership Tracker
 * row and then offers to ping the member on WhatsApp.
 */
class RecordPaymentFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_record_payment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val memberId = arguments?.getString("memberId").orEmpty()

        val memberLabel = view.findViewById<TextView>(R.id.memberLabel)
        val fy = view.findViewById<EditText>(R.id.fy)
        val amount = view.findViewById<EditText>(R.id.amount)
        val date = view.findViewById<EditText>(R.id.date)
        val receipt = view.findViewById<EditText>(R.id.receipt)
        val status = view.findViewById<EditText>(R.id.status)
        val save = view.findViewById<Button>(R.id.saveButton)
        val wa = view.findViewById<Button>(R.id.waButton)

        fy.setText(FinancialYear.currentLabel())
        date.setText(SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date()))
        status.setText("Paid")

        viewLifecycleOwner.lifecycleScope.launch {
            val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
            memberLabel.text = "${m.primaryName} · ${m.memberId}"
        }

        save.setOnClickListener {
            val fyLabel = FinancialYear.normalize(fy.text.toString()) ?: fy.text.toString().trim()
            val cell = FyCell(
                status = status.text.toString().trim(),
                amount = amount.text.toString().trim(),
                date = date.text.toString().trim(),
                receipt = receipt.text.toString().trim(),
            )
            viewLifecycleOwner.lifecycleScope.launch {
                val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
                ServiceLocator.membershipRepo.recordPayment(
                    memberId = m.memberId,
                    primaryName = m.primaryName,
                    fyLabel = fyLabel,
                    cell = cell,
                )
                PushWorker.enqueue(requireContext())
                Toast.makeText(requireContext(), R.string.payment_saved, Toast.LENGTH_SHORT).show()
            }
        }

        wa.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val m = ServiceLocator.memberRepo.get(memberId) ?: return@launch
                val msg = WhatsApp.paymentReceiptMessage(
                    memberName = m.primaryName,
                    fyLabel = fy.text.toString().trim(),
                    amount = amount.text.toString().trim(),
                    date = date.text.toString().trim(),
                    receiptNo = receipt.text.toString().trim(),
                )
                WhatsApp.send(requireContext(), m.primaryMobile, msg)
            }
        }

        // Drop back to detail after a successful save+navigate is intentional
        // — user can add another payment without re-navigating if they want.
        save.setOnLongClickListener {
            findNavController().popBackStack(); true
        }
    }
}
