package org.mmbs.tracker.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Parsed Google Service Account JSON key file.
 * Fields intentionally map the official Google-issued JSON schema — see
 * https://cloud.google.com/iam/docs/keys-create-delete
 */
@Serializable
data class ServiceAccountKey(
    @SerialName("type") val type: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("private_key_id") val privateKeyId: String,
    @SerialName("private_key") val privateKeyPem: String,
    @SerialName("client_email") val clientEmail: String,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token",
) {
    /**
     * Decode the PEM-encoded RSA private key into a java.security.PrivateKey
     * suitable for RS256 signing.
     */
    fun loadPrivateKey(): PrivateKey {
        val pem = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = android.util.Base64.decode(pem, android.util.Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(der)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(jsonText: String): ServiceAccountKey =
            json.decodeFromString(serializer(), jsonText)

        fun isPlaceholder(jsonText: String): Boolean =
            jsonText.contains("\"placeholder\"")
    }
}
