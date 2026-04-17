package com.phoneaios

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FloatingBubbleService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val promptEngine = PromptEngine()
    private val actionExecutor = ActionExecutor()
    private lateinit var windowManager: WindowManager
    private lateinit var aiBrain: AIBrain

    private var bubbleView: View? = null
    private var overlayView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var statusText: TextView? = null
    private var planText: TextView? = null
    private var edgeGlow: EdgeGlowView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        aiBrain = AIBrain(this)
        startForeground(1001, createNotification())
        createBubble()
        initRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        speechRecognizer?.destroy()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        aiBrain.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "phoneaios_bubble"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PhoneAIOS Bubble",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("PhoneAIOS bubble running")
            .setContentText("Tap the floating mic to control the phone offline.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun createBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 56
            y = 220
        }

        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble_btn, null)
        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (kotlin.math.abs(event.rawX - touchX) < 10 && kotlin.math.abs(event.rawY - touchY) < 10) {
                            startListening()
                        }
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(bubbleView, params)
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    showOverlay("Listening...", "")
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    edgeGlow?.setAmplitude(((rmsdB + 2f) / 10f).coerceAtLeast(0f))
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    hideOverlay()
                    aiBrain.speak("Speech recognition is unavailable.")
                }

                override fun onResults(results: Bundle?) {
                    val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (transcript == null) {
                        hideOverlay()
                        return
                    }
                    handleCommand(transcript)
                }

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
        showOverlay("Thinking...", command)
        aiBrain.speak("Working on it.")
        val heuristicActions = promptEngine.parseCommand(command)
        if (heuristicActions.isNotEmpty()) {
            executePlan(heuristicActions)
            return
        }

        serviceScope.launch {
            val screenContext = withContext(Dispatchers.Default) {
                ScreenParser().parseScreen(PhoneControlService.instance?.rootInActiveWindow)
            }
            val actions = withContext(Dispatchers.Default) {
                aiBrain.generateActions(command, screenContext)
            }
            if (actions.isEmpty()) {
                statusText?.text = "No plan generated"
                aiBrain.speak("I could not plan that request offline.")
                delay(1500L)
                hideOverlay()
            } else {
                executePlan(actions)
            }
        }
    }

    private fun executePlan(actions: List<Action>) {
        serviceScope.launch {
            actionExecutor.execute(actions, object : ActionExecutor.Callback {
                override fun onActionStart(action: Action) {
                    statusText?.text = "Executing"
                    planText?.alpha = 1f
                    planText?.text = action.spokenSummary
                }

                override suspend fun onSensitiveAction(action: Action): Boolean {
                    aiBrain.speak("Swipe to approve ${action.spokenSummary}")
                    return requestSwipeApproval(action)
                }

                override fun onComplete() {
                    statusText?.text = "Done"
                    edgeGlow?.flashSuccess()
                    aiBrain.speak("Action complete.")
                    serviceScope.launch {
                        delay(1000L)
                        hideOverlay()
                    }
                }
            })
        }
    }

    private suspend fun requestSwipeApproval(action: Action): Boolean {
        val result = CompletableDeferred<Boolean>()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        val approvalView = LayoutInflater.from(this).inflate(R.layout.confirmation_overlay, null)
        approvalView.findViewById<TextView>(R.id.safetyPlan).text = action.spokenSummary
        val swipeHandle = approvalView.findViewById<View>(R.id.swipeHandle)
        val swipeContainer = approvalView.findViewById<View>(R.id.swipeContainer)
        swipeHandle.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val targetX = (event.rawX - view.width / 2f)
                        .coerceIn(0f, (swipeContainer.width - view.width).toFloat())
                    view.x = targetX
                    if (targetX >= swipeContainer.width - view.width - 12f) {
                        runCatching { windowManager.removeView(approvalView) }
                        result.complete(true)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!result.isCompleted) {
                        view.animate().x(0f).setDuration(160L).start()
                    }
                    true
                }
                else -> false
            }
        }
        approvalView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            runCatching { windowManager.removeView(approvalView) }
            result.complete(false)
        }
        windowManager.addView(approvalView, params)
        return result.await()
    }

    private fun showOverlay(status: String, plan: String) {
        if (overlayView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
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
        statusText?.text = status
        planText?.text = plan
    }

    private fun hideOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        statusText = null
        planText = null
        edgeGlow = null
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
}
