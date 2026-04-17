package com.phoneaios

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceFeedbackManager(context: Context) {
    private var ready = false
    private var textToSpeech: TextToSpeech? = null

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                textToSpeech?.language = Locale.getDefault()
            }
        }
    }

    fun speak(message: String) {
        if (ready) {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "phoneaios")
        }
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
