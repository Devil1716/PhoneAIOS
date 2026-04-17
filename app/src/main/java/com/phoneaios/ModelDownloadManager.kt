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

enum class ModelType(val displayName: String, val fileName: String, val url: String) {
    GEMMA_1_1_2B("Gemma 1.1 2B (Default)", "gemma-2b-it.task", "https://huggingface.co/xianbao/mediapipe-gemma-2b-it/resolve/main/gemma-2b-it.task?download=1"),
    GEMMA_2_2B("Gemma 2 2B", "gemma-2-2b-it.task", "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma2-2b-it-cpu-int4.bin"),
    GEMMA_3_1B("Gemma 3 1B", "gemma-3-1b-it.task", "https://pub-8df053a4792c4d74b7f2e02a9250fd0e.r2.dev/gemma-3-1b-it.task"),
    GEMMA_3_270M("Gemma 3 270M (Ultra Light)", "gemma-3-270m-it.task", "https://pub-8df053a4792c4d74b7f2e02a9250fd0e.r2.dev/gemma-3-270m-it.task")
}

class ModelDownloadManager(private val context: Context) {
    private var currentModelType = ModelType.GEMMA_1_1_2B

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1L
    private var receiver: BroadcastReceiver? = null

    fun setModelType(type: ModelType) {
        currentModelType = type
    }

    fun getModelType(): ModelType = currentModelType

    fun resolveModelFile(): File? {
        val appPrivateDownloadFolder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val privateFile = File(appPrivateDownloadFolder, currentModelType.fileName)
        
        val legacyDownloadFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PhoneAIOS/${currentModelType.name}/${currentModelType.fileName}"
        )
        val appExternalFolder = File(context.getExternalFilesDir(null), currentModelType.fileName)
        val internalFile = File(context.filesDir, currentModelType.fileName)
        
        return listOf(privateFile, legacyDownloadFolder, appExternalFolder, internalFile)
            .firstOrNull { it.exists() && it.length() > 5 * 1024L * 1024L } // At least 5MB
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

        val request = DownloadManager.Request(Uri.parse(currentModelType.url))
            .setTitle("PhoneAIOS Model: ${currentModelType.displayName}")
            .setDescription("Downloading ${currentModelType.displayName} for on-device inference")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                currentModelType.fileName
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
