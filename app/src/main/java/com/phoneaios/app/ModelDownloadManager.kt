package com.phoneaios.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import java.io.File

class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val MODEL_URL = "https://huggingface.co/google/gemma-1.1-2b-it-gpu-int4/resolve/main/gemma-2b-it.task"
        private const val MODEL_FILENAME = "gemma-2b-it.task"
        private const val TAG = "ModelDownloadManager"
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1

    fun isModelDownloaded(): Boolean {
        val file = File(context.filesDir, MODEL_FILENAME)
        return file.exists() && file.length() > 1000000 // Simple check to ensure it's not a tiny error file
    }

    fun getModelFile(): File {
        return File(context.filesDir, MODEL_FILENAME)
    }

    fun startDownload(onComplete: () -> Unit) {
        if (isModelDownloaded()) {
            onComplete()
            return
        }

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Downloading AI Model")
            .setDescription("Gemma 2B model for PhoneAIOS")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, MODEL_FILENAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Log.d(TAG, "Download completed")
                    // Copy to internal files dir for safer access
                    val source = File(context?.getExternalFilesDir(null), MODEL_FILENAME)
                    val dest = File(context?.filesDir, MODEL_FILENAME)
                    if (source.exists()) {
                        source.copyTo(dest, overwrite = true)
                        source.delete()
                    }
                    onComplete()
                    context?.unregisterReceiver(this)
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}
