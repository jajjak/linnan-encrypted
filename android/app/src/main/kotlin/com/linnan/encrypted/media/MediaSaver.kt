package com.linnan.encrypted.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.linnan.encrypted.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed interface SaveResult {
    data class Success(val displayName: String) : SaveResult
    data class Failure(val messageJa: String) : SaveResult
}

/**
 * Downloads a resolved media URL and saves it into the shared Downloads/LinNan collection via
 * MediaStore, so it shows up in the device's normal Files/Gallery apps like any other download.
 * Uses the scoped-storage MediaStore API on Android 10+ and a legacy public-directory write
 * (guarded by the WRITE_EXTERNAL_STORAGE permission, requested only on those OS versions) on
 * Android 8-9, matching this app's minSdk 26.
 */
object MediaSaver {

    suspend fun save(
        context: Context,
        candidate: DownloadCandidate,
        suggestedBaseName: String
    ): SaveResult = withContext(Dispatchers.IO) {
        val extension = if (candidate.isVideo) "mp4" else "jpg"
        val fileName = "${sanitizeFileName(suggestedBaseName)}_${System.currentTimeMillis()}.$extension"

        val request = Request.Builder().url(candidate.url).get().build()
        val response = try {
            HttpClient.instance.newCall(request).execute()
        } catch (e: Exception) {
            return@withContext SaveResult.Failure("ダウンロード通信に失敗しました。電波状況を確認してください。")
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                return@withContext SaveResult.Failure("ダウンロードに失敗しました(応答: ${resp.code})。リンクの有効期限が切れている可能性があります。")
            }
            val bytes = resp.body?.bytes()
                ?: return@withContext SaveResult.Failure("ダウンロードしたデータが空でした。")

            return@withContext try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveViaMediaStoreQPlus(context, fileName, candidate, bytes)
                } else {
                    saveLegacy(context, fileName, candidate, bytes)
                }
            } catch (e: Exception) {
                SaveResult.Failure("端末への保存に失敗しました(${e.message ?: "不明なエラー"})。")
            }
        }
    }

    private fun saveViaMediaStoreQPlus(
        context: Context,
        fileName: String,
        candidate: DownloadCandidate,
        bytes: ByteArray
    ): SaveResult {
        val collection = if (candidate.isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val relativePath = if (candidate.isVideo) {
            Environment.DIRECTORY_MOVIES + "/LinNan"
        } else {
            Environment.DIRECTORY_PICTURES + "/LinNan"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, candidate.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val itemUri: Uri = resolver.insert(collection, values)
            ?: return SaveResult.Failure("端末のメディアストレージに保存先を作成できませんでした。")

        resolver.openOutputStream(itemUri)?.use { it.write(bytes) }
            ?: return SaveResult.Failure("保存先ファイルを開けませんでした。")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)

        return SaveResult.Success(fileName)
    }

    private fun saveLegacy(
        context: Context,
        fileName: String,
        candidate: DownloadCandidate,
        bytes: ByteArray
    ): SaveResult {
        val publicDir = Environment.getExternalStoragePublicDirectory(
            if (candidate.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        )
        val targetDir = File(publicDir, "LinNan")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return SaveResult.Failure("保存フォルダを作成できませんでした。ストレージへのアクセス許可を確認してください。")
        }
        val targetFile = File(targetDir, fileName)
        FileOutputStream(targetFile).use { it.write(bytes) }

        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(targetFile.absolutePath), arrayOf(candidate.mimeType), null
        )
        return SaveResult.Success(fileName)
    }

    private fun sanitizeFileName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").take(60).ifBlank { "linnan_media" }
}
