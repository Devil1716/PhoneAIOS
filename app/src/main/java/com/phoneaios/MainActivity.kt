package com.phoneaios

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var promptEngine: PromptEngine
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var modelDownloadManager: ModelDownloadManager
    private lateinit var aiBrain: AIBrain
    private var speechRecognizer: SpeechRecognizer? = null
    private val actionHistory = mutableListOf<String>()

    private lateinit var statusSub: TextView
    private lateinit var downloadStatus: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var openFolderButton: Button
    private lateinit var startBubbleButton: Button
    private lateinit var voiceButton: Button
    private lateinit var commandInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var actionLog: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var modelSpinner: Spinner
    private lateinit var accessibilityBtn: ImageButton

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        promptEngine = PromptEngine()
        actionExecutor = ActionExecutor()
        modelDownloadManager = ModelDownloadManager(this)
        aiBrain = AIBrain(this)

        bindViews()
        setupUi()
        requestRuntimePermissions()
        initRecognizer()
        updateStatus()
        setupModelSelection()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        modelDownloadManager.unregister()
        aiBrain.close()
        super.onDestroy()
    }

    private fun bindViews() {
        statusSub = findViewById(R.id.statusSub)
        downloadStatus = findViewById(R.id.downloadStatus)
        downloadProgress = findViewById(R.id.downloadProgress)
        downloadButton = findViewById(R.id.downloadButton)
        openFolderButton = findViewById(R.id.openFolderButton)
        startBubbleButton = findViewById(R.id.startBubbleButton)
        voiceButton = findViewById(R.id.voiceButton)
        commandInput = findViewById(R.id.commandInput)
        sendButton = findViewById(R.id.sendButton)
        actionLog = findViewById(R.id.actionLog)
        logScroll = findViewById(R.id.logScroll)
        modelSpinner = findViewById(R.id.modelSpinner)
        accessibilityBtn = findViewById(R.id.accessibilityBtn)
    }

    private fun setupModelSelection() {
        val models = ModelType.values()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models.map { it.displayName })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter
        
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                modelDownloadManager.setModelType(models[position])
                updateStatus()
                // Re-init brain with new model if it exists
                if (modelDownloadManager.isModelReady()) {
                    aiBrain = AIBrain(this@MainActivity)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupUi() {
        downloadButton.setOnClickListener {
            downloadButton.isEnabled = false
            downloadProgress.visibility = View.VISIBLE
            downloadStatus.text = getString(R.string.status_model_downloading)
            modelDownloadManager.enqueueDownload(
                onQueued = { appendLog("Queued Gemma model download to /sdcard/Download/PhoneAIOS/model/") },
                onComplete = { file ->
                    runOnUiThread {
                        downloadButton.isEnabled = true
                        downloadProgress.visibility = View.GONE
                        downloadStatus.text = "Model ready: ${file.absolutePath}"
                        appendLog("Gemma ready at ${file.absolutePath}")
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        downloadButton.isEnabled = true
                        downloadProgress.visibility = View.GONE
                        downloadStatus.text = error
                        appendLog(error)
                    }
                }
            )
        }

        openFolderButton.setOnClickListener {
            appendLog("Place gemma-2b-it.task in /sdcard/Download/PhoneAIOS/model/ and reopen the app.")
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Open the Downloads app to copy the model file.", Toast.LENGTH_LONG).show()
            }
        }

        startBubbleButton.setOnClickListener { startBubbleService() }
        voiceButton.setOnClickListener { startListening() }
        sendButton.setOnClickListener { submitTypedCommand() }
        accessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find PhoneAIOS and enable it", Toast.LENGTH_LONG).show()
        }
        commandInput.setOnEditorActionListener { _, _, _ ->
            submitTypedCommand()
            true
        }
    }

    private fun submitTypedCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isEmpty()) return
        commandInput.text?.clear()
        handleCommand(command)
    }

    private fun startBubbleService() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        appendLog("Floating mic bubble started.")
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    appendLog("Listening for a voice command...")
                }

                override fun onResults(results: Bundle?) {
                    val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (transcript != null) handleCommand(transcript)
                }

                override fun onError(error: Int) {
                    appendLog("Speech recognition failed with code $error")
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun handleCommand(command: String) {
        appendLog("User: $command")
        actionHistory.clear()
        
        val staticPlan = promptEngine.parseCommand(command)
        if (staticPlan.isNotEmpty()) {
            executePlan(staticPlan)
            return
        }
        
        if (!aiBrain.hasModel()) {
            appendLog("Gemma model is not ready yet. Download it first.")
            return
        }

        lifecycleScope.launch {
            var isFinished = false
            var steps = 0
            while (!isFinished && steps < 10) {
                val screenContext = withContext(Dispatchers.Default) {
                    ScreenParser().parseScreen(PhoneControlService.instance?.rootInActiveWindow)
                }
                val historyStr = if (actionHistory.isEmpty()) "(No history)" else actionHistory.joinToString("\n")
                
                val generatedActions = withContext(Dispatchers.Default) {
                    aiBrain.generateActions(command, screenContext, historyStr)
                }
                
                if (generatedActions.isEmpty()) {
                    appendLog("Agent could not decide next step.")
                    break
                }
                
                val action = generatedActions.first()
                actionHistory.add(action.spokenSummary)
                
                if (action.type == ActionType.ENTER && action.spokenSummary.contains("done", ignoreCase = true)) {
                    appendLog("Task Complete: ${action.spokenSummary}")
                    isFinished = true
                    break
                }

                executePlan(generatedActions)
                steps++
                delay(1500) // Wait for screen to settle before next step
            }
        }
    }

    private fun executePlan(actions: List<Action>) {
        appendLog("Plan has ${actions.size} steps.")
        lifecycleScope.launch {
            actionExecutor.execute(actions, object : ActionExecutor.Callback {
                override fun onActionStart(action: Action) {
                    appendLog("Executing: ${action.spokenSummary}")
                }

                override suspend fun onSensitiveAction(action: Action): Boolean {
                    appendLog("Sensitive action needs bubble approval: ${action.spokenSummary}")
                    return PhoneControlService.instance != null
                }

                override fun onComplete() {
                    appendLog("Sequence complete.")
                }
            })
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
        if (!packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return PhoneControlService.instance != null
    }

    private fun updateStatus() {
        val accEnabled = isAccessibilityEnabled()
        accessibilityBtn.setImageResource(if (accEnabled) android.R.drawable.ic_menu_manage else android.R.drawable.stat_notify_error)
        accessibilityBtn.imageTintList = android.content.res.ColorStateList.valueOf(
            if (accEnabled) ContextCompat.getColor(this, android.R.color.holo_green_light)
            else ContextCompat.getColor(this, android.R.color.holo_red_light)
        )

        statusSub.text = when {
            !accEnabled -> "Accessibility Service DISABLED"
            modelDownloadManager.isModelReady() -> getString(R.string.status_model_ready)
            else -> getString(R.string.status_permissions_needed)
        }
        downloadStatus.text = modelDownloadManager.resolveModelFile()?.absolutePath
            ?: getString(R.string.status_model_missing)
        downloadProgress.visibility = View.GONE
        downloadButton.isEnabled = true
    }

    private fun appendLog(message: String) {
        actionLog.append("\n> $message")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }
}
