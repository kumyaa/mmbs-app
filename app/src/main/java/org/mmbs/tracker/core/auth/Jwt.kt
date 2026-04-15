package org.mmbs.tracker.core.auth

import org.mmbs.tracker.core.util.Base64Url
import java.security.PrivateKey
import java.security.Signature

/**
 * Minimal RS256 JWT signer — just enough to authenticate a Google Service Account
 * against the OAuth2 token endpoint (RFC 7523 §2.1).
 *
 * We avoid a JWT library because the entire spec we need is ~15 lines: build a
 * two-segment "header.payload" string, sign it with RS256, append the signature.
 */
object Jwt {

    private const val HEADER_RS256 =
        """{"alg":"RS256","typ":"JWT"}"""

    /**
     * Build a signed JWT for the Service Account token-exchange flow.
     *
     * @param clientEmail    service account's client_email
     * @param scope          OAuth scope, space-separated if multiple
     * @param tokenUri       "aud" claim — must equal the token endpoint URL
     * @param privateKey     RSA private key decoded from the JSON key file
     * @param nowSec         current time, seconds since epoch
     * @param ttlSec         validity window; Google accepts up to 3600
     */
    fun buildServiceAccountAssertion(
        clientEmail: String,
        scope: String,
        tokenUri: String,
        privateKey: PrivateKey,
        nowSec: Long,
        ttlSec: Long = 3600L,
    ): String {
        val claims = buildString {
            append('{')
            append("\"iss\":\"").append(clientEmail).append("\",")
            append("\"scope\":\"").append(scope).append("\",")
            append("\"aud\":\"").append(tokenUri).append("\",")
            append("\"iat\":").append(nowSec).append(',')
            append("\"exp\":").append(nowSec + ttlSec)
            append('}')
        }

        val signingInput =
            Base64Url.encode(HEADER_RS256) + "." + Base64Url.encode(claims)

        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()

        return signingInput + "." + Base64Url.encode(sig)
    }
}
