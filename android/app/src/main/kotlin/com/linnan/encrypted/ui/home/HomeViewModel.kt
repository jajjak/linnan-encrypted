package com.linnan.encrypted.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linnan.encrypted.data.AuthRepository
import com.linnan.encrypted.media.DetectedPlatform
import com.linnan.encrypted.media.DownloadCandidate
import com.linnan.encrypted.media.InstagramRepository
import com.linnan.encrypted.media.MediaFetchResult
import com.linnan.encrypted.media.MediaInfo
import com.linnan.encrypted.media.MediaSaver
import com.linnan.encrypted.media.SaveResult
import com.linnan.encrypted.media.TikTokRepository
import com.linnan.encrypted.media.XRepository
import com.linnan.encrypted.media.YouTubeRepository
import com.linnan.encrypted.media.detectPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Analyzing : HomeUiState
    data class Found(val info: MediaInfo) : HomeUiState
    data class Error(val messageJa: String) : HomeUiState
}

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data object Saving : DownloadUiState
    data class Success(val fileName: String) : DownloadUiState
    data class Failure(val messageJa: String) : DownloadUiState
}

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val instagramRepository: InstagramRepository,
    private val xRepository: XRepository,
    private val youTubeRepository: YouTubeRepository,
    private val tikTokRepository: TikTokRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

    fun analyze(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _uiState.value = HomeUiState.Error("URLを入力してください。")
            return
        }
        val platform = detectPlatform(trimmed)
        if (platform == DetectedPlatform.UNKNOWN) {
            _uiState.value = HomeUiState.Error(
                "対応していないURLです。Instagram / YouTube / TikTok / X (Twitter) のURLを貼り付けてください。"
            )
            return
        }

        _uiState.value = HomeUiState.Analyzing
        _downloadState.value = DownloadUiState.Idle
        viewModelScope.launch {
            val result = when (platform) {
                DetectedPlatform.INSTAGRAM -> instagramRepository.resolve(trimmed)
                DetectedPlatform.X -> xRepository.resolve(trimmed)
                DetectedPlatform.YOUTUBE -> youTubeRepository.resolve(trimmed)
                DetectedPlatform.TIKTOK -> tikTokRepository.resolve(trimmed)
                DetectedPlatform.UNKNOWN -> null
            }
            _uiState.value = when (result) {
                is MediaFetchResult.Found -> HomeUiState.Found(result.info)
                is MediaFetchResult.Error -> HomeUiState.Error(result.messageJa)
                null -> HomeUiState.Error("内部エラーが発生しました。")
            }
        }
    }

    fun download(context: Context, info: MediaInfo, candidate: DownloadCandidate) {
        _downloadState.value = DownloadUiState.Saving
        viewModelScope.launch {
            val result = MediaSaver.save(context.applicationContext, candidate, info.title)
            _downloadState.value = when (result) {
                is SaveResult.Success -> DownloadUiState.Success(result.displayName)
                is SaveResult.Failure -> DownloadUiState.Failure(result.messageJa)
            }
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadUiState.Idle
    }
}
