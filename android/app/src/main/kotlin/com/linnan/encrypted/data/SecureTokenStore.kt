package com.linnan.encrypted.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.linnan.encrypted.oauth.AuthProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val obtainedAtEpochMs: Long,
    val expiresInSeconds: Long? = null,
    val accountLabel: String? = null
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val exp = expiresInSeconds ?: return false
        return nowEpochMs > obtainedAtEpochMs + exp * 1000
    }
}

/**
 * Stores only OAuth access/refresh tokens, at rest, encrypted with a key held in the
 * Android Keystore (never leaves the device, never backed up). The user's Instagram/Google/
 * TikTok/X *password* is never seen by this app at all - login happens entirely in the
 * system browser, so there is nothing password-shaped to store here in the first place.
 */
class SecureTokenStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "linnan_secure_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(provider: AuthProvider, token: StoredToken) {
        prefs.edit().putString(provider.storageKey, json.encodeToString(token)).apply()
    }

    fun get(provider: AuthProvider): StoredToken? {
        val raw = prefs.getString(provider.storageKey, null) ?: return null
        return try {
            json.decodeFromString(StoredToken.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    fun isLoggedIn(provider: AuthProvider): Boolean = get(provider) != null

    fun clear(provider: AuthProvider) {
        prefs.edit().remove(provider.storageKey).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
