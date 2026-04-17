package com.phoneaios

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AIBrain(context: Context) {
    private val voiceFeedbackManager = VoiceFeedbackManager(context)

    private val llmInference: LlmInference? = ModelDownloadManager(context).resolveModelFile()?.let { modelFile ->
        runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setMaxTopK(40)
                .build()
            LlmInference.createFromOptions(context, options)
        }.getOrElse { error ->
            Log.e("AIBrain", "Failed to initialize Gemma: ${error.message}", error)
            null
        }
    }

    fun hasModel(): Boolean = llmInference != null

    fun speak(message: String) {
        voiceFeedbackManager.speak(message)
    }

    private fun loadPrompt(name: String): String {
        return runCatching {
            context.assets.open("prompts/$name").bufferedReader().use { it.readText() }
        }.getOrElse { "" }
    }

    fun close() {
        llmInference?.close()
        voiceFeedbackManager.shutdown()
    }

    fun generateActions(task: String, screenContext: String, history: String = "(No history)"): List<Action> {
        val inference = llmInference ?: return emptyList()
        val deciderPromptTemplate = loadPrompt("decider_en.md")
        if (deciderPromptTemplate.isEmpty()) return emptyList()

        val fullPrompt = deciderPromptTemplate
            .replace("{task}", task)
            .replace("{history}", history) + "\n\nSCREEN_CONTEXT:\n$screenContext"

        val raw = runCatching { inference.generateResponse(fullPrompt) }.getOrElse { error ->
            Log.e("AIBrain", "Decider inference failed: ${error.message}", error)
            return emptyList()
        }
        
        Log.d("AIBrain", "Raw Decider Response: $raw")
        return parseDeciderResponse(raw)
    }

    private fun parseDeciderResponse(raw: String): List<Action> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        
        return runCatching {
            val json = JSONObject(raw.substring(start, end + 1))
            val actionName = json.getString("action")
            val params = json.optJSONObject("parameters") ?: JSONObject()
            val reasoning = json.optString("reasoning", "No reasoning provided")

            val type = when (actionName.lowercase()) {
                "click" -> ActionType.CLICK_TEXT
                "input" -> ActionType.TYPE_TEXT
                "swipe" -> ActionType.SWIPE
                "wait" -> ActionType.WAIT
                "done" -> ActionType.ENTER // Or a specific success type
                "long_press" -> ActionType.LONG_PRESS
                else -> ActionType.WAIT
            }

            listOf(Action(
                type = type,
                text = params.optString("target_element").takeIf { it.isNotEmpty() } ?: params.optString("text"),
                spokenSummary = reasoning
            ))
        }.getOrElse { emptyList() }
    }

    private fun org.json.JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }
}
