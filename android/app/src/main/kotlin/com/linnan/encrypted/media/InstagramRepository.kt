package com.linnan.encrypted.media

import com.linnan.encrypted.data.AuthRepository
import com.linnan.encrypted.data.network.HttpClient
import com.linnan.encrypted.oauth.AuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Uses the official Instagram Graph API ("Instagram API with Instagram Login"), which - by
 * Meta's own design - only ever exposes the *signed-in user's own* media via `/me/media`.
 * There is no scope or endpoint that returns someone else's private or public post; this is
 * a hard platform limit, not a restriction we impose. That happens to line up exactly with
 * "only download your own content", so no bypass of any kind is needed or attempted here.
 *
 * Requirement (out of our control): the logged-in account must be an Instagram
 * Business or Creator account for the Graph API to return anything at all - a plain
 * personal account is not supported by Meta's public API as of the 2024 API consolidation.
 */
class InstagramRepository(private val authRepository: AuthRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolve(url: String): MediaFetchResult = withContext(Dispatchers.IO) {
        val token = authRepository.accessToken(AuthProvider.INSTAGRAM)
            ?: return@withContext MediaFetchResult.Error("先に設定画面からInstagramにログインしてください。")

        val listUrl = "https://graph.instagram.com/me/media" +
            "?fields=id,caption,media_type,media_url,thumbnail_url,permalink,timestamp" +
            "&access_token=$token"
        val request = Request.Builder().url(listUrl).get().build()

        val response = try {
            HttpClient.instance.newCall(request).execute()
        } catch (e: Exception) {
            return@withContext MediaFetchResult.Error("Instagramへの通信に失敗しました。電波状況を確認してください。")
        }

        response.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext MediaFetchResult.Error(
                    "Instagramからメディア情報を取得できませんでした(応答: ${resp.code})。" +
                        "ログインが期限切れの可能性があります。設定画面から再ログインしてください。"
                )
            }
            val items = runCatching { json.parseToJsonElement(bodyStr).jsonObject["data"]?.jsonArray }
                .getOrNull() ?: return@withContext MediaFetchResult.Error("Instagramの応答を解析できませんでした。")

            val match = items.map { it.jsonObject }.firstOrNull { item ->
                val permalink = item["permalink"]?.jsonPrimitive?.content
                permalink != null && (url.trimEnd('/') == permalink.trimEnd('/') || url.contains(permalink))
            }

            if (match == null) {
                return@withContext MediaFetchResult.Found(
                    MediaInfo(
                        platform = DetectedPlatform.INSTAGRAM,
                        title = "投稿が見つかりません",
                        author = authRepository.accountLabel(AuthProvider.INSTAGRAM),
                        thumbnailUrl = null,
                        sourceUrl = url,
                        downloadCandidates = emptyList(),
                        unavailableReasonJa = "この投稿はログイン中のアカウント自身の投稿として見つかりませんでした。" +
                            "Instagramの公式APIはログインした本人の投稿のみ取得を許可しているため、" +
                            "他人の投稿・非公開の投稿は本アプリでは取得できません。"
                    )
                )
            }

            val mediaType = match["media_type"]?.jsonPrimitive?.content
            val mediaUrl = match["media_url"]?.jsonPrimitive?.content
            val thumbnail = match["thumbnail_url"]?.jsonPrimitive?.content ?: mediaUrl
            val caption = match["caption"]?.jsonPrimitive?.content ?: "(キャプションなし)"

            if (mediaUrl == null) {
                return@withContext MediaFetchResult.Found(
                    MediaInfo(
                        platform = DetectedPlatform.INSTAGRAM,
                        title = caption,
                        author = authRepository.accountLabel(AuthProvider.INSTAGRAM),
                        thumbnailUrl = thumbnail,
                        sourceUrl = url,
                        downloadCandidates = emptyList(),
                        unavailableReasonJa = "この投稿の形式(アルバム/カルーセルなど)は現在のAPI応答に" +
                            "ダウンロード可能なメディアURLを含んでいませんでした。"
                    )
                )
            }

            MediaFetchResult.Found(
                MediaInfo(
                    platform = DetectedPlatform.INSTAGRAM,
                    title = caption,
                    author = authRepository.accountLabel(AuthProvider.INSTAGRAM),
                    thumbnailUrl = thumbnail,
                    sourceUrl = url,
                    downloadCandidates = listOf(
                        DownloadCandidate(
                            label = if (mediaType == "VIDEO") "元動画(最高画質)" else "元画像(最高画質)",
                            url = mediaUrl,
                            mimeType = if (mediaType == "VIDEO") "video/mp4" else "image/jpeg",
                            isVideo = mediaType == "VIDEO"
                        )
                    )
                )
            )
        }
    }
}
