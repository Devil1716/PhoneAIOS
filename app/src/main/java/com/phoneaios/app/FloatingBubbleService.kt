package com.phoneaios.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.widget.ImageView
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var overlayView: View? = null
    private var edgeGlow: EdgeGlowView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val actionExecutor = ActionExecutor()
    private val promptEngine = PromptEngine()
    private var voiceManager: VoiceFeedbackManager? = null
    private var aiBrain: AIBrain? = null
    private var screenParser = ScreenParser()
    private var statusText: android.widget.TextView? = null
    private var planText: android.widget.TextView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createBubble()
        initSpeech()
        aiBrain = AIBrain(this)
        voiceManager = VoiceFeedbackManager(this)
    }

    private fun createBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble_btn, null)
        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = event.rawX - initialTouchX
                        val diffY = event.rawY - initialTouchY
                        if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                            toggleListening()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(bubbleView, params)
    }

    private fun initSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { showOverlay() }
            override fun onRmsChanged(rmsdB: Float) {
                val amp = (rmsdB + 2f) / 10f
                edgeGlow?.setAmplitude(amp)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { processCommand(it) }
                hideOverlay()
            }
            override fun onError(error: Int) { hideOverlay() }
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun toggleListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processCommand(cmd: String) {
        val actions = promptEngine.parseCommand(cmd)
        if (actions.isNotEmpty()) {
            executeSequence(actions)
        } else {
            // Fallback to LLM with Perception
            showOverlay()
            statusText?.text = "Perceiving screen..."
            
            val screenContext = screenParser.parseScreen(PhoneControlService.instance?.rootInActiveWindow)
            
            statusText?.text = "Thinking..."
            CoroutineScope(Dispatchers.IO).launch {
                val aiActions = aiBrain?.generateActions(cmd, screenContext) ?: emptyList()
                withContext(Dispatchers.Main) {
                    if (aiActions.isNotEmpty()) {
                        executeSequence(aiActions)
                    } else {
                        statusText?.text = "Could not parse command"
                        kotlinx.coroutines.delay(2000)
                        hideOverlay()
                    }
                }
            }
        }
    }

    private var lastApprovalTime = 0L
    private val TRUST_DURATION_MS = 5 * 60 * 1000 // 5 minutes

    private fun executeSequence(actions: List<Action>) {
        showOverlay()
        CoroutineScope(Dispatchers.Main).launch {
            actionExecutor.execute(actions, object : ActionExecutor.ActionCallback {
                override fun onActionStarted(action: Action) {
                    val status = "AI: ${action.type.name.lowercase().replace("_", " ")}"
                    planText?.alpha = 1f
                    planText?.text = status
                    statusText?.text = "Executing..."
                }

                override suspend fun onSafetyCheckRequired(action: Action): Boolean {
                    voiceManager?.speak("I need your approval for this action.")
                    // Trusted Mode check
                    if (System.currentTimeMillis() - lastApprovalTime < TRUST_DURATION_MS) {
                        return true
                    }

                    // Show Confirmation Overlay
                    val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    showConfirmationOverlay(action) { approved ->
                        deferred.complete(approved)
                    }
                    val result = deferred.await()
                    if (result) lastApprovalTime = System.currentTimeMillis()
                    return result
                }

                override fun onSequenceComplete() {
                    voiceManager?.speak("Action complete")
                    planText?.text = "Complete"
                    edgeGlow?.flashSuccess()
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(1000)
                        hideOverlay()
                    }
                }
            })
        }
    }

    private fun showConfirmationOverlay(action: Action, callback: (Boolean) -> Unit) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        val safetyView = LayoutInflater.from(this).inflate(R.layout.confirmation_overlay, null)
        val plan = safetyView.findViewById<android.widget.TextView>(R.id.safetyPlan)
        plan.text = "AI plans to: ${action.type.name} ${action.text ?: ""}"
        
        val handle = safetyView.findViewById<View>(R.id.swipeHandle)
        val container = safetyView.findViewById<View>(R.id.swipeContainer)
        
        handle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX - v.width / 2
                    if (newX > 0 && newX < container.width - v.width) {
                        v.x = newX
                    }
                    if (newX >= container.width - v.width - 20) {
                        // Success!
                        windowManager.removeView(safetyView)
                        callback(true)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (v.x < container.width - v.width) v.animate().x(0f).start()
                    true
                }
                else -> true
            }
        }
        
        safetyView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            windowManager.removeView(safetyView)
            callback(false)
        }
        
        windowManager.addView(safetyView, params)
    }

    private fun showOverlay() {
        if (overlayView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                    else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            overlayView = LayoutInflater.from(this).inflate(R.layout.listening_overlay, null)
            edgeGlow = overlayView?.findViewById(R.id.edgeGlow)
            statusText = overlayView?.findViewById(R.id.statusText)
            planText = overlayView?.findViewById(R.id.planText)
            windowManager.addView(overlayView, params)
        }
    }

    private fun hideOverlay() {
        overlayView?.let { 
            windowManager.removeView(it)
            overlayView = null
            edgeGlow = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        hideOverlay()
        speechRecognizer?.destroy()
        voiceManager?.shutdown()
    }
}
