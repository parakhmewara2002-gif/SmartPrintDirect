package com.smartprint.direct.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val expectedSha256: String = ""
)

class InAppUpdateManager(private val context: Context) {

    private val _updateState = MutableStateFlow<UpdateInfo?>(null)
    val updateState: StateFlow<UpdateInfo?> = _updateState.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    suspend fun checkForUpdates(
        manifestUrl: String = "https://raw.githubusercontent.com/parakhmewara2002-gif/SmartPrintDirect/main/version.json"
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            val url = URL(manifestUrl)
            Log.d("InAppUpdate", "Checking update at $manifestUrl (Current version code: $currentVersionCode)")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "SmartPrintDirect-Android")
            }

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val remoteCode = json.optInt("versionCode", 0)
                val remoteName = json.optString("versionName", "1.0.0")
                val notes = json.optString("changelog", "Bug fixes and performance enhancements")
                val apkUrl = json.optString("apkUrl", "")
                val sha = json.optString("expectedSha256", "")

                val update = UpdateInfo(
                    hasUpdate = remoteCode > currentVersionCode,
                    latestVersionCode = remoteCode,
                    latestVersionName = remoteName,
                    releaseNotes = notes,
                    downloadUrl = apkUrl,
                    expectedSha256 = sha
                )
                _updateState.value = update
                return@withContext update
            }
        } catch (e: Exception) {
            Log.w("InAppUpdate", "Could not check for update: ${e.message}")
        }
        null
    }

    fun startUpdateDownload(apkUrl: String, expectedSha256: String) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadProgress.value = 10

        val fileName = "SmartPrintDirect_Update.apk"
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("HP Smart Direct Update")
            .setDescription("Downloading latest update securely...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination))

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    _isDownloading.value = false
                    _downloadProgress.value = 100
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}

                    if (!destination.exists()) {
                        Log.e("InAppUpdate", "Downloaded APK missing")
                        return
                    }

                    if (expectedSha256.isNotBlank() && expectedSha256.length == 64) {
                        val computed = calculateSha256(destination)
                        if (!computed.equals(expectedSha256, ignoreCase = true)) {
                            Log.e("InAppUpdate", "Hash verification failed: $computed != $expectedSha256")
                            destination.delete()
                            return
                        }
                    }

                    installDownloadedApk(destination)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    fun installDownloadedApk(file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("InAppUpdate", "Failed to launch package installer", e)
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
