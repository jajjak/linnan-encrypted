package com.linnan.encrypted.oauth

import com.linnan.encrypted.BuildConfig

/** The four services the app can authenticate against. */
enum class AuthProvider(val storageKey: String, val displayNameJa: String) {
    INSTAGRAM("instagram", "Instagram"),
    YOUTUBE("youtube", "YouTube"),
    TIKTOK("tiktok", "TikTok"),
    X("x", "X (Twitter)")
}

/**
 * Everything needed to drive one provider's browser-based OAuth flow.
 *
 * [clientId] is public by design (OAuth client IDs are not secrets). No provider config here
 * ever includes a client *secret* - Instagram's flow, which is the only one of the four whose
 * token exchange requires one, is completed by the backend (see AuthRepository) instead of
 * on-device.
 */
data class OAuthConfig(
    val provider: AuthProvider,
    val clientId: String,
    val authorizeUrl: String,
    val scope: String,
    val redirectUri: String,
    /** true = code exchange needs a confidential client secret, so it happens on the backend. */
    val requiresBackendExchange: Boolean,
    /** Only used when requiresBackendExchange is false. */
    val tokenUrl: String? = null
)

object OAuthConfigs {

    private const val REDIRECT_SCHEME = "com.linnan.encrypted"

    fun redirectUri(provider: AuthProvider) = "$REDIRECT_SCHEME://oauth/${provider.storageKey}/callback"

    fun forProvider(provider: AuthProvider): OAuthConfig = when (provider) {
        AuthProvider.INSTAGRAM -> OAuthConfig(
            provider = provider,
            clientId = BuildConfig.INSTAGRAM_CLIENT_ID,
            // "Instagram API with Instagram Login" (Meta, 2024+) direct authorize endpoint.
            authorizeUrl = "https://www.instagram.com/oauth/authorize",
            scope = "instagram_business_basic",
            // The backend owns this redirect URI (must be an https URL registered in the
            // Meta app dashboard); it exchanges the code server-side, then 302s the Custom
            // Tab to redirectUri(INSTAGRAM) below to hand control back to the app.
            redirectUri = "${BuildConfig.IG_BACKEND_BASE_URL}/api/auth/instagram/callback",
            requiresBackendExchange = true
        )
        AuthProvider.YOUTUBE -> OAuthConfig(
            provider = provider,
            clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
            authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth",
            // Read-only: list/metadata of the signed-in user's own channel only.
            scope = "https://www.googleapis.com/auth/youtube.readonly",
            redirectUri = redirectUri(provider),
            requiresBackendExchange = false,
            tokenUrl = "https://oauth2.googleapis.com/token"
        )
        AuthProvider.TIKTOK -> OAuthConfig(
            provider = provider,
            clientId = BuildConfig.TIKTOK_CLIENT_KEY,
            authorizeUrl = "https://www.tiktok.com/v2/auth/authorize",
            scope = "user.info.basic,video.list",
            redirectUri = redirectUri(provider),
            requiresBackendExchange = false,
            tokenUrl = "https://open.tiktokapis.com/v2/oauth/token/"
        )
        AuthProvider.X -> OAuthConfig(
            provider = provider,
            clientId = BuildConfig.X_CLIENT_ID,
            authorizeUrl = "https://twitter.com/i/oauth2/authorize",
            scope = "tweet.read users.read offline.access",
            redirectUri = redirectUri(provider),
            requiresBackendExchange = false,
            tokenUrl = "https://api.twitter.com/2/oauth2/token"
        )
    }
}
