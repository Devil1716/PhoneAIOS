package com.phoneaios.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.phoneaios.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private val actionExecutor = ActionExecutor()
    private val promptEngine = PromptEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        setupUI()
        initSpeechRecognizer()
    }

    private fun setupUI() {
        binding.micButton.setOnClickListener {
            startFloatingService()
        }

        binding.commandInput.setOnEditorActionListener { _, _, _ ->
            val cmd = binding.commandInput.text.toString()
            if (cmd.isNotEmpty()) {
                executeCommand(cmd)
                binding.commandInput.text.clear()
            }
            true
        }
    }

    private fun startFloatingService() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && 
            !android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                android.net.Uri.parse("package:$packageName"))
            startActivityForResult(intent, 123)
            Toast.makeText(this, "Please allow overlay permission", Toast.LENGTH_SHORT).show()
        } else {
            startService(Intent(this, FloatingBubbleService::class.java))
            Toast.makeText(this, "Floating Bubble Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.actionLog.append("\n> Listening...")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { executeCommand(it) }
            }
            override fun onError(error: Int) {
                binding.actionLog.append("\n> Error: Speech recognition failed")
            }
            // Other overrides omitted for brevity
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        startPulsingEffect()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun startPulsingEffect() {
        val scaleX = android.animation.ObjectAnimator.ofFloat(binding.micButton, "scaleX", 1f, 1.2f, 1f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(binding.micButton, "scaleY", 1f, 1.2f, 1f)
        scaleX.repeatCount = android.animation.ObjectAnimator.INFINITE
        scaleY.repeatCount = android.animation.ObjectAnimator.INFINITE
        scaleX.duration = 1000
        scaleY.duration = 1000
        scaleX.start()
        scaleY.start()
        binding.micButton.tag = listOf(scaleX, scaleY)
    }

    private fun stopPulsingEffect() {
        (binding.micButton.tag as? List<android.animation.ObjectAnimator>)?.forEach { it.cancel() }
        binding.micButton.scaleX = 1f
        binding.micButton.scaleY = 1f
    }

    private fun executeCommand(cmd: String) {
        stopPulsingEffect()
        binding.actionLog.append("\n> User: $cmd")
        val actions = promptEngine.parseCommand(cmd)
        if (actions.isNotEmpty()) {
            binding.actionLog.append("\n> AI: Planning sequence (${actions.size} steps)")
            lifecycleScope.launch {
                actionExecutor.execute(actions)
                binding.actionLog.append("\n> AI: Sequence complete")
            }
        } else {
            binding.actionLog.append("\n> AI: Command not recognized via pattern matching. Using LLM...")
            // Fallback to AIBrain here
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        }

        // Install packages permission is special (not a standard runtime permission)
        if (!packageManager.canRequestPackageInstalls()) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
            Toast.makeText(this, "Please allow PhoneAIOS to install other apps", Toast.LENGTH_LONG).show()
        }
    }
}
