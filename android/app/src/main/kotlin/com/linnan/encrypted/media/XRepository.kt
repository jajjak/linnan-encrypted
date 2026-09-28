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
 * Uses the official X (Twitter) API v2 with the logged-in user's own OAuth 2.0 user-context
 * token - never scraping the public web page. Note (out of our control): X's v2 API has
 * required a paid developer tier for meaningful read access since 2023; a free-tier token may
 * get an authorization error from X itself, which is surfaced to the user as-is rather than
 * worked around.
 */
class XRepository(private val authRepository: AuthRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun extractTweetId(url: String): String? =
        Regex("status(?:es)?/(\\d+)").find(url)?.groupValues?.get(1)

    suspend fun resolve(url: String): MediaFetchResult = withContext(Dispatchers.IO) {
        val token = authRepository.accessToken(AuthProvider.X)
            ?: return@withContext MediaFetchResult.Error("先に設定画面からXにログインしてください。")
        val tweetId = extractTweetId(url)
            ?: return@withContext MediaFetchResult.Error("投稿(ポスト)のURLからIDを読み取れませんでした。")

        val apiUrl = "https://api.x.com/2/tweets/$tweetId" +
            "?expansions=attachments.media_keys,author_id" +
            "&media.fields=variants,url,preview_image_url,type,duration_ms" +
            "&user.fields=username" +
            "&tweet.fields=text"
        val request = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val response = try {
            HttpClient.instance.newCall(request).execute()
        } catch (e: Exception) {
            return@withContext MediaFetchResult.Error("Xへの通信に失敗しました。電波状況を確認してください。")
        }

        response.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext MediaFetchResult.Error(
                    "Xからの取得に失敗しました(応答: ${resp.code})。ログインの期限切れ、" +
                        "非公開アカウントの投稿、またはXの開発者アカウントのAPIアクセス権限不足の可能性があります。"
                )
            }
            val root = runCatching { json.parseToJsonElement(bodyStr).jsonObject }.getOrNull()
                ?: return@withContext MediaFetchResult.Error("Xの応答を解析できませんでした。")

            val text = root["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "(本文なし)"
            val authorId = root["data"]?.jsonObject?.get("author_id")?.jsonPrimitive?.content
            val username = root["includes"]?.jsonObject?.get("users")?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["id"]?.jsonPrimitive?.content == authorId }
                ?.get("username")?.jsonPrimitive?.content

            val mediaList = root["includes"]?.jsonObject?.get("media")?.jsonArray

            if (mediaList == null || mediaList.isEmpty()) {
                return@withContext MediaFetchResult.Found(
                    MediaInfo(
                        platform = DetectedPlatform.X,
                        title = text,
                        author = username,
                        thumbnailUrl = null,
                        sourceUrl = url,
                        downloadCandidates = emptyList(),
                        unavailableReasonJa = "この投稿には画像・動画が添付されていません。"
                    )
                )
            }

            val candidates = mutableListOf<DownloadCandidate>()
            var thumbnail: String? = null
            for (mediaEl in mediaList) {
                val media = mediaEl.jsonObject
                val type = media["type"]?.jsonPrimitive?.content
                thumbnail = thumbnail ?: media["preview_image_url"]?.jsonPrimitive?.content ?: media["url"]?.jsonPrimitive?.content
                if (type == "photo") {
                    media["url"]?.jsonPrimitive?.content?.let {
                        candidates += DownloadCandidate("元画像(最高画質)", it, "image/jpeg", isVideo = false)
                    }
                } else if (type == "video" || type == "animated_gif") {
                    val variants = media["variants"]?.jsonArray.orEmpty()
                    val best = variants
                        .map { it.jsonObject }
                        .filter { it["content_type"]?.jsonPrimitive?.content == "video/mp4" }
                        .maxByOrNull { it["bit_rate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }
                    best?.get("url")?.jsonPrimitive?.content?.let {
                        val bitRate = best["bit_rate"]?.jsonPrimitive?.content?.toLongOrNull()
                        candidates += DownloadCandidate(
                            label = if (bitRate != null) "最高画質(${bitRate / 1000}kbps)" else "最高画質",
                            url = it,
                            mimeType = "video/mp4",
                            isVideo = true
                        )
                    }
                }
            }

            MediaFetchResult.Found(
                MediaInfo(
                    platform = DetectedPlatform.X,
                    title = text,
                    author = username,
                    thumbnailUrl = thumbnail,
                    sourceUrl = url,
                    downloadCandidates = candidates,
                    unavailableReasonJa = if (candidates.isEmpty())
                        "添付メディアの直リンクを取得できませんでした。" else null
                )
            )
        }
    }
}
