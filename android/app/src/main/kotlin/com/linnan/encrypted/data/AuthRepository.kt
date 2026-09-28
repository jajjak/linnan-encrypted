package com.linnan.encrypted.data

import android.content.Context
import android.net.Uri
import com.linnan.encrypted.BuildConfig
import com.linnan.encrypted.data.network.HttpClient
import com.linnan.encrypted.oauth.AuthProvider
import com.linnan.encrypted.oauth.CustomTabsLauncher
import com.linnan.encrypted.oauth.OAuthConfig
import com.linnan.encrypted.oauth.OAuthConfigs
import com.linnan.encrypted.oauth.OAuthResultBus
import com.linnan.encrypted.oauth.Pkce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

sealed interface LoginOutcome {
    data class Success(val accountLabel: String?) : LoginOutcome
    data class Failure(val messageJa: String) : LoginOutcome
    data object Cancelled : LoginOutcome
}

/**
 * Drives the whole browser-based login flow for one provider at a time: build the authorize
 * URL, open it in Custom Tabs, wait for the redirect, exchange the code for a token (directly
 * for YouTube/TikTok/X, via the backend for Instagram, since only Instagram's exchange needs
 * a confidential client secret), and persist the result. Never touches a WebView, never sees
 * a password.
 */
class AuthRepository(private val appContext: Context) {

    private val tokenStore = SecureTokenStore(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    private val _loginStates = MutableStateFlow(
        AuthProvider.entries.associateWith { tokenStore.isLoggedIn(it) }
    )
    val loginStates: StateFlow<Map<AuthProvider, Boolean>> = _loginStates

    fun accountLabel(provider: AuthProvider): String? = tokenStore.get(provider)?.accountLabel

    fun accessToken(provider: AuthProvider): String? = tokenStore.get(provider)?.accessToken

    suspend fun login(provider: AuthProvider): LoginOutcome {
        val cfg = OAuthConfigs.forProvider(provider)
        if (cfg.clientId.isBlank()) {
            return LoginOutcome.Failure(
                "${provider.displayNameJa} のクライアントIDが設定されていません。" +
                    "開発者向け設定(gradle.properties)に登録済みのOAuthクライアントIDを追加してください。"
            )
        }

        val state = Pkce.newState()
        val verifier = Pkce.newCodeVerifier()
        val authorizeUrl = buildAuthorizeUrl(cfg, state, verifier)

        CustomTabsLauncher.launch(appContext, authorizeUrl)

        val redirect = withTimeoutOrNull(5 * 60 * 1000L) {
            OAuthResultBus.redirects
                .filter { it.toString().startsWith(cfg.redirectUriPrefixForMatching()) }
                .first()
        } ?: return LoginOutcome.Cancelled

        return try {
            handleRedirect(cfg, state, verifier, redirect)
        } catch (e: IOException) {
            LoginOutcome.Failure("通信エラーが発生しました。電波状況を確認して、もう一度お試しください。")
        } catch (e: Exception) {
            LoginOutcome.Failure("ログイン処理に失敗しました(${e.message ?: "不明なエラー"})。もう一度お試しください。")
        }
    }

    fun logout(provider: AuthProvider) {
        tokenStore.clear(provider)
        _loginStates.value = _loginStates.value.toMutableMap().apply { put(provider, false) }
    }

    private fun OAuthConfig.redirectUriPrefixForMatching(): String {
        // Instagram's redirect comes back through our own scheme too (the backend 302s to it
        // after the confidential exchange), so all four providers share the same matching rule.
        return OAuthConfigs.redirectUri(provider)
    }

    private suspend fun handleRedirect(
        cfg: OAuthConfig,
        expectedState: String,
        verifier: String,
        redirect: Uri
    ): LoginOutcome {
        redirect.getQueryParameter("error")?.let { error ->
            return LoginOutcome.Failure(mapOAuthErrorToJapanese(error))
        }

        return if (cfg.requiresBackendExchange) {
            val handoff = redirect.getQueryParameter("handoff")
                ?: return LoginOutcome.Failure("Instagramからの応答が正しくありません。もう一度お試しください。")
            fetchInstagramTokenFromBackend(handoff)
        } else {
            val returnedState = redirect.getQueryParameter("state")
            if (returnedState != expectedState) {
                return LoginOutcome.Failure("ログイン要求の検証に失敗しました(state不一致)。セキュリティのため中断しました。")
            }
            val code = redirect.getQueryParameter("code")
                ?: return LoginOutcome.Failure("${cfg.provider.displayNameJa} からの応答に認証コードが含まれていません。")
            exchangeCodeDirectly(cfg, code, verifier)
        }
    }

    private suspend fun exchangeCodeDirectly(cfg: OAuthConfig, code: String, verifier: String): LoginOutcome =
        withContext(Dispatchers.IO) {
            val tokenUrl = cfg.tokenUrl ?: return@withContext LoginOutcome.Failure("設定エラー: トークンURLがありません。")
            val form = FormBody.Builder()
                .add("client_id", cfg.clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", cfg.redirectUri)
                .add("code_verifier", verifier)
                .build()
            val request = Request.Builder().url(tokenUrl).post(form).build()

            HttpClient.instance.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext LoginOutcome.Failure(
                        "${cfg.provider.displayNameJa} のログインに失敗しました(サーバー応答: ${response.code})。" +
                            "アプリの登録設定(クライアントID/リダイレクトURI)を確認してください。"
                    )
                }
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val accessToken = root["access_token"]?.jsonPrimitive?.content
                    ?: return@withContext LoginOutcome.Failure("${cfg.provider.displayNameJa} のトークン応答が不正です。")
                val refreshToken = root["refresh_token"]?.jsonPrimitive?.content
                val expiresIn = root["expires_in"]?.jsonPrimitive?.content?.toLongOrNull()

                tokenStore.save(
                    cfg.provider,
                    StoredToken(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        obtainedAtEpochMs = System.currentTimeMillis(),
                        expiresInSeconds = expiresIn,
                        accountLabel = null
                    )
                )
                _loginStates.value = _loginStates.value.toMutableMap().apply { put(cfg.provider, true) }
                LoginOutcome.Success(accountLabel = null)
            }
        }

    private suspend fun fetchInstagramTokenFromBackend(handoff: String): LoginOutcome =
        withContext(Dispatchers.IO) {
            val url = "${BuildConfig.IG_BACKEND_BASE_URL}/api/auth/instagram/token/$handoff"
            val request = Request.Builder().url(url).get().build()
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LoginOutcome.Failure(
                        "Instagramログインの完了処理に失敗しました(サーバー応答: ${response.code})。" +
                            "時間をおいてもう一度お試しください。"
                    )
                }
                val bodyStr = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val accessToken = root["accessToken"]?.jsonPrimitive?.content
                    ?: return@withContext LoginOutcome.Failure("Instagramのトークン応答が不正です。")
                val username = root["username"]?.jsonPrimitive?.content
                val expiresIn = root["expiresIn"]?.jsonPrimitive?.content?.toLongOrNull()

                tokenStore.save(
                    AuthProvider.INSTAGRAM,
                    StoredToken(
                        accessToken = accessToken,
                        obtainedAtEpochMs = System.currentTimeMillis(),
                        expiresInSeconds = expiresIn,
                        accountLabel = username
                    )
                )
                _loginStates.value = _loginStates.value.toMutableMap().apply { put(AuthProvider.INSTAGRAM, true) }
                LoginOutcome.Success(accountLabel = username)
            }
        }

    private fun buildAuthorizeUrl(cfg: OAuthConfig, state: String, verifier: String): String {
        val builder = Uri.parse(cfg.authorizeUrl).buildUpon()
            .appendQueryParameter("client_id", cfg.clientId)
            .appendQueryParameter("redirect_uri", cfg.redirectUri)
            .appendQueryParameter("scope", cfg.scope)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
        if (!cfg.requiresBackendExchange) {
            builder
                .appendQueryParameter("code_challenge", Pkce.codeChallengeS256(verifier))
                .appendQueryParameter("code_challenge_method", "S256")
        }
        return builder.build().toString()
    }

    private fun mapOAuthErrorToJapanese(error: String): String = when (error) {
        "access_denied", "user_cancelled", "user_denied" -> "ログインがキャンセルされました。"
        "invalid_scope" -> "アプリの権限設定(スコープ)が正しくありません。開発者設定を確認してください。"
        "server_error", "temporarily_unavailable" -> "サービス側で一時的な問題が発生しています。しばらくしてからもう一度お試しください。"
        else -> "ログインに失敗しました($error)。"
    }
}
