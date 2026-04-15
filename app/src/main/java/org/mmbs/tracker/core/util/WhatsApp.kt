package org.mmbs.tracker.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

/**
 * Minimal wa.me helper (PRD §6). We intentionally don't bind to the WhatsApp
 * package by name — using the universal wa.me URL keeps the app independent of
 * WhatsApp vs WhatsApp Business and works even if the package isn't installed
 * (falls back to browser → web WhatsApp).
 */
object WhatsApp {

    /** Open a WhatsApp chat with [phoneE164WithoutPlus] and a pre-filled [message]. */
    fun send(ctx: Context, phoneE164WithoutPlus: String, message: String) {
        val digits = phoneE164WithoutPlus.filter { it.isDigit() }
        val url = "https://wa.me/$digits?text=${URLEncoder.encode(message, "UTF-8")}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "No app found to open WhatsApp link", Toast.LENGTH_SHORT).show()
        }
    }

    /** Build a canonical payment-receipt message body (PRD §6 template). */
    fun paymentReceiptMessage(
        memberName: String,
        fyLabel: String,
        amount: String,
        date: String,
        receiptNo: String,
    ): String = buildString {
        append("Namaskar ")
        append(memberName)
        append(",\n\nMMBS has received your membership fee of Rs. ")
        append(amount)
        append(" for ")
        append(fyLabel)
        append(" on ")
        append(date)
        append(".\nReceipt #: ")
        append(receiptNo)
        append("\n\nThank you,\nMarathi Mandal Bengaluru South")
    }
}
