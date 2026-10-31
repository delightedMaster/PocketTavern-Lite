package me.rerere.rikkahub.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.http.await
import me.rerere.rikkahub.APP_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

private const val TAG = "UpdateChecker"
private const val NOTIFICATION_ID_UPDATE = 10086

private const val RELEASES_URL =
    "https://api.github.com/repos/delightedMaster/PocketTavern-Lite/releases/latest"

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Downloading(
        val download: UpdateDownload,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float,
    ) : DownloadStatus
    data class Success(val download: UpdateDownload, val file: File) : DownloadStatus
    data class Failed(val download: UpdateDownload, val message: String) : DownloadStatus
}

class UpdateChecker(
    private val client: OkHttpClient,
    private val appScope: AppScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    val updateState: StateFlow<UiState<UpdateInfo>> = checkUpdate().stateIn(
        scope = appScope,
        started = SharingStarted.Lazily,
        initialValue = UiState.Loading,
    )

    private fun checkUpdate(): Flow<UiState<UpdateInfo>> = flow {
        emit(UiState.Loading)
        val state = client.newCall(
            Request.Builder()
                .url(RELEASES_URL)
                .get()
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader(
                    "User-Agent",
                    "PocketTavern-Lite ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                )
                .build()
        ).await().use { response ->
            when {
                // GitHub returns 404 when this repository has no published stable release yet.
                response.code == 404 -> UiState.Idle
                response.isSuccessful -> UiState.Success(
                    json.decodeFromString<GitHubRelease>(response.body.string()).toUpdateInfo()
                )
                else -> error("PocketTavern-Lite update check failed (HTTP ${response.code})")
            }
        }
        emit(state)
    }.catch {
        emit(UiState.Error(it))
    }.flowOn(Dispatchers.IO)

    fun downloadUpdate(context: Context, download: UpdateDownload) {
        if (!download.isApk) {
            context.openUrl(download.url)
            return
        }

        val currentStatus = _downloadStatus.value
        if (currentStatus is DownloadStatus.Downloading && currentStatus.download.url == download.url) {
            Toast.makeText(context, context.getString(R.string.update_card_downloading), Toast.LENGTH_SHORT).show()
            return
        }

        if (currentStatus is DownloadStatus.Success && currentStatus.download.url == download.url && currentStatus.file.exists()) {
            installApk(context, currentStatus.file)
            return
        }

        val appContext = context.applicationContext
        appScope.launch(Dispatchers.IO) {
            performDownload(appContext, download)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        }.onFailure { e ->
            Log.e(TAG, "Failed to start install intent", e)
            Toast.makeText(context, "无法启动安装程序: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun performDownload(context: Context, download: UpdateDownload) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, download.name)
        val tmpFile = File(updatesDir, "${download.name}.tmp")
        val expectedSize = download.size.toLongOrNull() ?: 0L

        if (apkFile.exists() && expectedSize > 0 && apkFile.length() == expectedSize) {
            _downloadStatus.value = DownloadStatus.Success(download, apkFile)
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
            return
        }

        val notificationManager = NotificationManagerCompat.from(context)

        val candidateUrls = buildList {
            add(download.url)
            if (download.url.contains("github.com") && !download.url.contains("gh-proxy.com")) {
                add("https://gh-proxy.com/${download.url}")
            }
        }

        var downloadedSuccessfully = false
        var lastError: Exception? = null

        _downloadStatus.value = DownloadStatus.Downloading(
            download = download,
            downloadedBytes = 0L,
            totalBytes = expectedSize,
            progress = 0f
        )

        for (url in candidateUrls) {
            try {
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader(
                        "User-Agent",
                        "PocketTavern-Lite ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                    )
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    throw IOException("HTTP $code")
                }

                val body = response.body
                val totalBytes = if (body.contentLength() > 0) body.contentLength() else expectedSize

                var downloadedBytes = 0L
                var lastUpdateMillis = 0L
                var lastPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            val percent = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else -1

                            if (now - lastUpdateMillis > 300 || percent != lastPercent) {
                                lastUpdateMillis = now
                                lastPercent = percent
                                val progressFloat = if (totalBytes > 0) {
                                    (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                } else 0f

                                _downloadStatus.value = DownloadStatus.Downloading(
                                    download = download,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    progress = progressFloat
                                )

                                runCatching {
                                    val progressText = if (totalBytes > 0) {
                                        "$percent% (${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)})"
                                    } else {
                                        formatFileSize(downloadedBytes)
                                    }
                                    val builder = NotificationCompat.Builder(context, APP_UPDATE_NOTIFICATION_CHANNEL_ID)
                                        .setSmallIcon(R.mipmap.ic_launcher)
                                        .setContentTitle(context.getString(R.string.app_name))
                                        .setContentText(progressText)
                                        .setProgress(100, if (percent >= 0) percent else 0, percent < 0)
                                        .setOngoing(true)
                                        .setOnlyAlertOnce(true)
                                    notificationManager.notify(NOTIFICATION_ID_UPDATE, builder.build())
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (tmpFile.exists() && tmpFile.length() > 0) {
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }
                    if (tmpFile.renameTo(apkFile)) {
                        downloadedSuccessfully = true
                        break
                    } else {
                        throw IOException("Failed to rename temporary download file")
                    }
                } else {
                    throw IOException("Downloaded file is empty")
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Download attempt failed from $url", e)
            }
        }

        if (downloadedSuccessfully && apkFile.exists()) {
            _downloadStatus.value = DownloadStatus.Success(download, apkFile)

            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val completeNotification = NotificationCompat.Builder(context, APP_UPDATE_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.update_download_success))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .build()
                notificationManager.notify(NOTIFICATION_ID_UPDATE, completeNotification)
            }

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        } else {
            val errorMsg = lastError?.localizedMessage ?: "Download failed"
            _downloadStatus.value = DownloadStatus.Failed(download, errorMsg)

            runCatching {
                val failNotification = NotificationCompat.Builder(context, APP_UPDATE_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.update_download_failed, errorMsg))
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .build()
                notificationManager.notify(NOTIFICATION_ID_UPDATE, failNotification)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.update_download_failed, errorMsg),
                    Toast.LENGTH_LONG
                ).show()
                context.openUrl(download.url)
            }
        }
    }
}

@Serializable
internal data class GitHubRelease(
    val tag_name: String = "",
    val body: String = "",
    val published_at: String = "",
    val html_url: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0,
)

internal fun GitHubRelease.toUpdateInfo(): UpdateInfo {
    val apkDownloads = assets
        .filter { it.name.endsWith(".apk", ignoreCase = true) && it.browser_download_url.isNotBlank() }
        .map { asset ->
            UpdateDownload(
                name = asset.name,
                url = asset.browser_download_url,
                size = asset.size.toString(),
            )
        }
    val downloads = apkDownloads.ifEmpty {
        if (html_url.isBlank()) emptyList() else listOf(
            UpdateDownload(
                name = "PocketTavern-Lite · GitHub Release",
                url = html_url,
                size = "0",
            )
        )
    }
    return UpdateInfo(
        version = tag_name.removePrefix("v").removePrefix("V"),
        publishedAt = published_at,
        changelog = body,
        downloads = downloads,
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String
) {
    val isApk: Boolean
        get() = name.endsWith(".apk", ignoreCase = true)

    val formattedSize: String
        get() {
            val bytes = size.toLongOrNull() ?: 0L
            return if (bytes > 0) formatFileSize(bytes) else size
        }
}

@Serializable
data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>
)

/**
 * 版本号值类，封装版本号字符串并提供比较功能
 *
 * 支持完整的 SemVer 规范：MAJOR.MINOR.PATCH[-prerelease][+build]
 * - 预发布版本优先级低于正式版：1.0.0-alpha < 1.0.0
 * - 预发布标识符按段逐个比较：数字按数值比较，字符串按字典序比较
 * - 预发布标识符优先级：alpha < beta < rc（通过字典序自然满足）
 * - build metadata（+号后面的部分）不影响优先级比较
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {

    private fun parse(): ParsedVersion {
        // 去掉 build metadata（+号后面的部分）
        val withoutBuild = value.split("+").first()
        // 分离主版本号和预发布标识符
        val hyphenIndex = withoutBuild.indexOf('-')
        val (coreStr, prereleaseStr) = if (hyphenIndex >= 0) {
            withoutBuild.substring(0, hyphenIndex) to withoutBuild.substring(hyphenIndex + 1)
        } else {
            withoutBuild to null
        }
        val core = coreStr.split(".").map { it.toIntOrNull() ?: 0 }
        val prerelease = prereleaseStr?.split(".")
        return ParsedVersion(core, prerelease)
    }

    override fun compareTo(other: Version): Int {
        val a = this.parse()
        val b = other.parse()

        // 先比较主版本号
        val maxLen = maxOf(a.core.size, b.core.size)
        for (i in 0 until maxLen) {
            val ap = if (i < a.core.size) a.core[i] else 0
            val bp = if (i < b.core.size) b.core[i] else 0
            if (ap != bp) return ap.compareTo(bp)
        }

        // 主版本号相同时比较预发布标识符
        // 有预发布标识符的版本优先级低于没有的：1.0.0-alpha < 1.0.0
        return when {
            a.prerelease == null && b.prerelease == null -> 0
            a.prerelease != null && b.prerelease == null -> -1
            a.prerelease == null && b.prerelease != null -> 1
            else -> comparePrerelease(a.prerelease!!, b.prerelease!!)
        }
    }

    companion object {
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            val maxLen = maxOf(a.size, b.size)
            for (i in 0 until maxLen) {
                // 字段少的优先级更低：1.0.0-alpha < 1.0.0-alpha.1
                if (i >= a.size) return -1
                if (i >= b.size) return 1

                val aNum = a[i].toIntOrNull()
                val bNum = b[i].toIntOrNull()

                val cmp = when {
                    // 都是字：按数值比较
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    // 数字优先级低于字符串
                    aNum != null -> -1
                    bNum != null -> 1
                    // 都是字符串：按字典序比较
                    else -> a[i].compareTo(b[i])
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<String>?,
)

// 扩展操作符函数，使比较更直观
operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))
