package com.sorush.banktracker.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.sorush.banktracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean
)

object AppUpdater {

    // Raw GitHub URL to version.json in the repository
    private const val VERSION_URL = "https://raw.githubusercontent.com/sorush71/bank-sms-tracker/main/version.json"

    suspend fun checkForUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(VERSION_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("خطا در برقراری ارتباط با سرور گیت‌هاب (کد: ${conn.responseCode})"))
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()

            val json = JSONObject(sb.toString())
            val latestCode = json.optInt("version_code", 1)
            val latestName = json.optString("version_name", "1.0.0")
            val downloadUrl = json.optString("download_url", "")
            val releaseNotes = json.optString("release_notes", "")

            val currentCode = BuildConfig.VERSION_CODE
            val isAvailable = latestCode > currentCode

            Result.success(
                UpdateInfo(
                    versionCode = latestCode,
                    versionName = latestName,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    isUpdateAvailable = isAvailable
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun downloadAndInstallApk(context: Context, downloadUrl: String, versionName: String) {
        val appContext = context.applicationContext
        val fileName = "BankTracker-v$versionName.apk"
        val destination = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destination.exists()) {
            destination.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("دانلود نسخه جدید برنامه حسابداری")
            .setDescription("در حال دریافت نسخه $versionName...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // Register receiver for when download finishes to prompt install
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        ctxt.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    installApk(ctxt, destination)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            appContext.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
