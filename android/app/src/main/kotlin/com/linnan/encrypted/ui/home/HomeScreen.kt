package com.linnan.encrypted.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.linnan.encrypted.media.DownloadCandidate
import com.linnan.encrypted.media.MediaInfo
import com.linnan.encrypted.ui.theme.ErrorRed
import com.linnan.encrypted.ui.theme.Gold
import com.linnan.encrypted.ui.theme.SuccessGreen

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    var pendingCandidate by remember { mutableStateOf<Pair<MediaInfo, DownloadCandidate>?>(null) }
    val legacyStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingCandidate
        pendingCandidate = null
        if (granted && pending != null) {
            viewModel.download(context, pending.first, pending.second)
        }
    }

    fun requestDownload(info: MediaInfo, candidate: DownloadCandidate) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingCandidate = info to candidate
            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.download(context, info, candidate)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("林男ダウンロード", style = MaterialTheme.typography.titleLarge, color = Gold)
        Text(
            "LinNan Media Downloader",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("Instagram / YouTube / TikTok / X のURL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.analyze(urlText) },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is HomeUiState.Analyzing
        ) {
            Text("解析する")
        }

        Spacer(Modifier.height(20.dp))

        when (val state = uiState) {
            HomeUiState.Idle -> Text(
                "自分のコンテンツ、または保存する権利があり取得が許可されているコンテンツのURLを貼り付けてください。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            HomeUiState.Analyzing -> LoadingRow("メディア情報を取得しています…")
            is HomeUiState.Error -> ErrorBanner(state.messageJa)
            is HomeUiState.Found -> ResultCard(
                info = state.info,
                downloadState = downloadState,
                onDownloadClick = { candidate -> requestDownload(state.info, candidate) }
            )
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = ErrorRed,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ResultCard(
    info: MediaInfo,
    downloadState: DownloadUiState,
    onDownloadClick: (DownloadCandidate) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            info.thumbnailUrl?.let { thumb ->
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(info.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            info.author?.let {
                Spacer(Modifier.height(4.dp))
                Text("投稿者: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))

            if (info.downloadCandidates.isEmpty()) {
                ErrorBanner(info.unavailableReasonJa ?: "ダウンロード可能なメディアが見つかりませんでした。")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(info.downloadCandidates) { candidate ->
                        Button(
                            onClick = { onDownloadClick(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = downloadState !is DownloadUiState.Saving
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${candidate.label} をダウンロード")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            when (downloadState) {
                DownloadUiState.Idle -> Unit
                DownloadUiState.Saving -> LoadingRow("端末に保存しています…")
                is DownloadUiState.Success -> Text(
                    "保存しました: ${downloadState.fileName}",
                    color = SuccessGreen,
                    style = MaterialTheme.typography.bodyMedium
                )
                is DownloadUiState.Failure -> ErrorBanner(downloadState.messageJa)
            }
        }
    }
}
