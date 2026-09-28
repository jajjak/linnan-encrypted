package com.linnan.encrypted.oauth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * RFC 7636 PKCE helper. This is what lets Instagram/Google/TikTok/X logins run entirely
 * through the system browser (Custom Tabs) without ever embedding a client secret or
 * the user's password inside the app.
 */
object Pkce {

    private val secureRandom = SecureRandom()

    fun newCodeVerifier(): String {
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return urlSafeBase64(bytes)
    }

    fun codeChallengeS256(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return urlSafeBase64(digest)
    }

    fun newState(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return urlSafeBase64(bytes)
    }

    private fun urlSafeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
}
