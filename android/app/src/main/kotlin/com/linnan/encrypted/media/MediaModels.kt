package com.linnan.encrypted.media

import com.linnan.encrypted.oauth.AuthProvider

enum class DetectedPlatform(val label: String) {
    INSTAGRAM("Instagram"),
    YOUTUBE("YouTube"),
    TIKTOK("TikTok"),
    X("X (Twitter)"),
    UNKNOWN("不明")
}

data class DownloadCandidate(
    val label: String,
    val url: String,
    val mimeType: String,
    val isVideo: Boolean
)

data class MediaInfo(
    val platform: DetectedPlatform,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val downloadCandidates: List<DownloadCandidate>,
    /** Non-null when downloadCandidates is empty: the honest, user-facing reason why. */
    val unavailableReasonJa: String? = null
)

/** Result of asking a repository to resolve a pasted URL. Never throws to the UI layer. */
sealed interface MediaFetchResult {
    data class Found(val info: MediaInfo) : MediaFetchResult
    data class Error(val messageJa: String) : MediaFetchResult
}

fun detectPlatform(url: String): DetectedPlatform {
    val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return DetectedPlatform.UNKNOWN
    return when {
        host.contains("instagram.com") -> DetectedPlatform.INSTAGRAM
        host.contains("youtube.com") || host.contains("youtu.be") -> DetectedPlatform.YOUTUBE
        host.contains("tiktok.com") -> DetectedPlatform.TIKTOK
        host.contains("twitter.com") || host.contains("x.com") -> DetectedPlatform.X
        else -> DetectedPlatform.UNKNOWN
    }
}

fun DetectedPlatform.toAuthProvider(): AuthProvider? = when (this) {
    DetectedPlatform.INSTAGRAM -> AuthProvider.INSTAGRAM
    DetectedPlatform.YOUTUBE -> AuthProvider.YOUTUBE
    DetectedPlatform.TIKTOK -> AuthProvider.TIKTOK
    DetectedPlatform.X -> AuthProvider.X
    DetectedPlatform.UNKNOWN -> null
}
