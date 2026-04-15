package org.mmbs.tracker.core.util

import android.util.Base64

/** URL-safe, no-padding Base64 encode — what JWT and OAuth2 want. */
object Base64Url {
    fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun encode(text: String): String = encode(text.toByteArray(Charsets.UTF_8))
}
