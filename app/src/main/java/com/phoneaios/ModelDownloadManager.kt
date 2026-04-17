package com.phoneaios

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.File

class ModelDownloadManager(private val context: Context) {
    companion object {
        const val MODEL_FILE_NAME = "gemma-2b-it.task"
        private const val MODEL_URL =
            "https://huggingface.co/xianbao/mediapipe-gemma-2b-it/resolve/main/gemma-2b-it.task?download=1"
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1L
    private var receiver: BroadcastReceiver? = null

    fun resolveModelFile(): File? {
        val downloadFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PhoneAIOS/model/$MODEL_FILE_NAME"
        )
        val appExternalFolder = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)
        val internalFile = File(context.filesDir, MODEL_FILE_NAME)
        return listOf(downloadFolder, appExternalFolder, internalFile)
            .firstOrNull { it.exists() && it.length() > 1024L * 1024L }
    }

    fun isModelReady(): Boolean = resolveModelFile() != null

    fun enqueueDownload(
        onQueued: () -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        resolveModelFile()?.let {
            onComplete(it)
            return
        }

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("PhoneAIOS model")
            .setDescription("Downloading Gemma 2B model for on-device inference")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "PhoneAIOS/model/$MODEL_FILE_NAME"
            )

        downloadId = downloadManager.enqueue(request)
        onQueued()

        receiver?.let {
            runCatching { context.unregisterReceiver(it) }
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                val file = resolveModelFile()
                if (file != null) {
                    onComplete(file)
                } else {
                    onError("Model download finished but the file could not be found.")
                }
                unregister()
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }
}
