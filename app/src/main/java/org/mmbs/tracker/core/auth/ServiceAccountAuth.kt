package org.mmbs.tracker.core.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.PrivateKey

/**
 * Holds the bundled service-account credential and mints short-lived OAuth2
 * access tokens on demand (cached in memory until near-expiry).
 *
 * Not thread-safe by accident — the [mutex] serialises refreshes so concurrent
 * callers share one in-flight token exchange.
 */
class ServiceAccountAuth(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: String = "https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.readonly",
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    // Loaded lazily; isPlaceholder=true means the APK was built without the real JSON key.
    @Volatile private var key: ServiceAccountKey? = null
    @Volatile private var privateKey: PrivateKey? = null
    @Volatile private var placeholder: Boolean = false

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedExpirySec: Long = 0

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    class AuthNotConfiguredException : IllegalStateException(
        "Service account JSON is a placeholder. Add SERVICE_ACCOUNT_JSON secret to the repo and rebuild."
    )

    /** Warm the in-memory key on the IO dispatcher. Safe to call repeatedly. */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (key != null || placeholder) return@withContext
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        if (ServiceAccountKey.isPlaceholder(text)) {
            placeholder = true
            return@withContext
        }
        val parsed = ServiceAccountKey.parse(text)
        privateKey = parsed.loadPrivateKey()
        key = parsed
    }

    /**
     * Returns a valid access token. Refreshes when the cached token is within
     * [REFRESH_BEFORE_SEC] seconds of expiry.
     */
    suspend fun accessToken(): String {
        ensureLoaded()
        val k = key ?: throw AuthNotConfiguredException()
        val pk = privateKey ?: throw AuthNotConfiguredException()

        val now = clock()
        cachedToken?.let { tok ->
            if (now < cachedExpirySec - REFRESH_BEFORE_SEC) return tok
        }

        return mutex.withLock {
            val now2 = clock()
            cachedToken?.let { tok ->
                if (now2 < cachedExpirySec - REFRESH_BEFORE_SEC) return@withLock tok
            }
            val (tok, expSec) = mintToken(k, pk, now2)
            cachedToken = tok
            cachedExpirySec = expSec
            tok
        }
    }

    /** Whether the bundled JSON is the placeholder (auth will fail). */
    suspend fun isPlaceholder(): Boolean {
        ensureLoaded()
        return placeholder
    }

    private suspend fun mintToken(
        k: ServiceAccountKey,
        pk: PrivateKey,
        nowSec: Long,
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        val assertion = Jwt.buildServiceAccountAssertion(
            clientEmail = k.clientEmail,
            scope = scope,
            tokenUri = k.tokenUri,
            privateKey = pk,
            nowSec = nowSec,
        )
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", assertion)
            .build()

        val req = Request.Builder().url(k.tokenUri).post(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Token exchange failed: HTTP ${resp.code} — $text")
            }
            val parsed: TokenResponse = json.decodeFromString(TokenResponse.serializer(), text)
            parsed.accessToken to (nowSec + parsed.expiresIn)
        }
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("token_type") val tokenType: String = "Bearer",
    )

    companion object {
        const val ASSET_PATH = "service_account.json"
        private const val REFRESH_BEFORE_SEC = 60L
    }
}
