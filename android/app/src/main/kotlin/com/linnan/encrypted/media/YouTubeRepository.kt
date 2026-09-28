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
 * YouTube Data API v3 gives video *metadata* (title, channel, thumbnail) but, by Google's own
 * design, has no endpoint that returns a downloadable media file URL for any video - including
 * the signed-in user's own uploads. (The only official way to get your own original upload
 * file back is the "Download" button in YouTube Studio's web UI, which is not part of any
 * public API.) So this repository always returns real metadata plus an honest explanation
 * instead of a fake or scraped download link - building a scraper here would mean reverse
 * engineering YouTube's player response, which is exactly the access-control bypass the task
 * explicitly rules out.
 */
class YouTubeRepository(private val authRepository: AuthRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun extractVideoId(url: String): String? {
        Regex("[?&]v=([\\w-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("youtu\\.be/([\\w-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("shorts/([\\w-]{11})").find(url)?.let { return it.groupValues[1] }
        return null
    }

    suspend fun resolve(url: String): MediaFetchResult = withContext(Dispatchers.IO) {
        val token = authRepository.accessToken(AuthProvider.YOUTUBE)
            ?: return@withContext MediaFetchResult.Error("先に設定画面からYouTube(Google)にログインしてください。")
        val videoId = extractVideoId(url)
            ?: return@withContext MediaFetchResult.Error("YouTubeの動画URLからIDを読み取れませんでした。")

        val apiUrl = "https://www.googleapis.com/youtube/v3/videos" +
            "?part=snippet,contentDetails&id=$videoId"
        val request = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val response = try {
            HttpClient.instance.newCall(request).execute()
        } catch (e: Exception) {
            return@withContext MediaFetchResult.Error("YouTubeへの通信に失敗しました。電波状況を確認してください。")
        }

        response.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext MediaFetchResult.Error(
                    "YouTubeからメディア情報を取得できませんでした(応答: ${resp.code})。" +
                        "ログインが期限切れの可能性があります。"
                )
            }
            val item = runCatching {
                json.parseToJsonElement(bodyStr).jsonObject["items"]?.jsonArray?.firstOrNull()?.jsonObject
            }.getOrNull() ?: return@withContext MediaFetchResult.Error(
                "動画が見つかりませんでした。非公開・限定公開、または削除された動画の可能性があります。"
            )

            val snippet = item["snippet"]?.jsonObject
            val title = snippet?.get("title")?.jsonPrimitive?.content ?: "(タイトル不明)"
            val channel = snippet?.get("channelTitle")?.jsonPrimitive?.content
            val thumbnail = snippet?.get("thumbnails")?.jsonObject
                ?.get("high")?.jsonObject?.get("url")?.jsonPrimitive?.content

            MediaFetchResult.Found(
                MediaInfo(
                    platform = DetectedPlatform.YOUTUBE,
                    title = title,
                    author = channel,
                    thumbnailUrl = thumbnail,
                    sourceUrl = url,
                    downloadCandidates = emptyList(),
                    unavailableReasonJa = "YouTube公式APIは動画ファイルそのもののダウンロードを提供していません" +
                        "(仕様上の制限であり、本アプリ側の不具合ではありません)。" +
                        "自分がアップロードした動画の元ファイルが必要な場合は、" +
                        "YouTube Studio(パソコン版サイト)の「ダウンロード」機能をご利用ください。"
                )
            )
        }
    }
}
