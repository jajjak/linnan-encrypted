package com.linnan.encrypted.media

import com.linnan.encrypted.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * TikTok's public oEmbed endpoint (no login required, no scraping - this is an endpoint
 * TikTok itself publishes for embedding posts) gives title/author/thumbnail metadata.
 * TikTok's authenticated APIs (Display API / Content Posting API) are for listing a
 * logged-in creator's own videos and for posting - as of this app's Data API research,
 * none of TikTok's public developer APIs expose a raw downloadable video file URL, even for
 * the video owner's own account (that access level is restricted to TikTok's separately
 * vetted Research API partner program). So, honestly: metadata only here, no invented
 * download link.
 */
class TikTokRepository {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolve(url: String): MediaFetchResult = withContext(Dispatchers.IO) {
        val oembedUrl = "https://www.tiktok.com/oembed".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("url", url)
            ?.build()
            ?: return@withContext MediaFetchResult.Error("URLの形式が正しくありません。")

        val request = Request.Builder().url(oembedUrl).get().build()
        val response = try {
            HttpClient.instance.newCall(request).execute()
        } catch (e: Exception) {
            return@withContext MediaFetchResult.Error("TikTokへの通信に失敗しました。電波状況を確認してください。")
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                return@withContext MediaFetchResult.Error(
                    "TikTokからメディア情報を取得できませんでした(応答: ${resp.code})。" +
                        "動画が非公開、または削除されている可能性があります。"
                )
            }
            val bodyStr = resp.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(bodyStr).jsonObject }.getOrNull()
                ?: return@withContext MediaFetchResult.Error("TikTokの応答を解析できませんでした。")

            val title = root["title"]?.jsonPrimitive?.content ?: "(タイトル不明)"
            val author = root["author_name"]?.jsonPrimitive?.content
            val thumbnail = root["thumbnail_url"]?.jsonPrimitive?.content

            MediaFetchResult.Found(
                MediaInfo(
                    platform = DetectedPlatform.TIKTOK,
                    title = title,
                    author = author,
                    thumbnailUrl = thumbnail,
                    sourceUrl = url,
                    downloadCandidates = emptyList(),
                    unavailableReasonJa = "TikTokの公式APIには、動画ファイル自体をダウンロードできる" +
                        "エンドポイントが(投稿者本人の動画であっても)一般開発者向けには公開されていません" +
                        "(仕様上の制限であり、本アプリ側の不具合ではありません)。"
                )
            )
        }
    }
}
