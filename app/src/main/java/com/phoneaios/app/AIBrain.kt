package com.phoneaios.app

import android.content.Context
import com.google.mediapipe.tasks.text.llminference.LlmInference
import java.io.File

class AIBrain(private val context: Context) {
    private var llmInference: LlmInference? = null

    init {
        val modelPath = "/data/local/tmp/gemma-2b-it.task" // Default recommended for large models
        val assetsFile = File(context.assets.list("")?.find { it == "gemma-2b-it.task" } ?: "")
        
        // This is a simplified initialization. 
        // Real implementation would check assets or download.
        if (assetsFile.exists()) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(assetsFile.absolutePath)
                .setMaxTokens(512)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
        }
    }

    fun parseActionsFromText(text: String): List<Action> {
        val actions = mutableListOf<Action>()
        try {
            // Extract the first JSON array found in the text (handles LLM preamble)
            val jsonStart = text.indexOf("[")
            val jsonEnd = text.lastIndexOf("]")
            if (jsonStart == -1 || jsonEnd == -1) return emptyList()
            
            val jsonArray = org.json.JSONArray(text.substring(jsonStart, jsonEnd + 1))
                val type = ActionType.valueOf(obj.getString("type"))
                val textValue = if (obj.has("text")) obj.getString("text") else null
                val pkgValue = if (obj.has("packageName")) obj.getString("packageName") else null
                
                // Sensitivity detection logic
                val isSensitive = when {
                    type == ActionType.TYPE_TEXT -> true // Typing is usually sensitive
                    type == ActionType.OPEN_APP && (pkgValue?.contains("settings") == true || pkgValue?.contains("whatsapp") == true) -> true
                    else -> false
                }

                actions.add(Action(
                    type = type,
                    text = textValue,
                    packageName = pkgValue,
                    duration = if (obj.has("duration")) obj.getLong("duration") else 0L,
                    isSensitive = isSensitive
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("AIBrain", "Failed to parse JSON: ${e.message}")
        }
        return actions
    }

    fun generateActions(prompt: String, screenContext: String = ""): List<Action> {
        val fullPrompt = """
            $screenContext
            
            User Command: "$prompt"
            Convert this command to JSON actions based on the current screen content.
            Format: [{"type": "CLICK_TEXT", "text": "..."}, {"type": "WAIT", "duration": 1000}]
            Available types: OPEN_APP, CLICK_TEXT, TYPE_TEXT, TAP, SWIPE, WAIT, ENTER, SCROLL
            If a button isn't visible, use SCROLL.
        """.trimIndent()
        val response = llmInference?.generateResponse(fullPrompt) ?: ""
        return parseActionsFromText(response)
    }
}
