package com.phoneaios

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONArray

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

    fun close() {
        llmInference?.close()
        voiceFeedbackManager.shutdown()
    }

    fun generateActions(prompt: String, screenContext: String): List<Action> {
        val inference = llmInference ?: return emptyList()
        val plannerPrompt = """
            You are PhoneAIOS, an offline Android agent.
            Convert the user request into a JSON array of actions.
            Only use these action types: OPEN_APP, CLICK_TEXT, TYPE_TEXT, WAIT, ENTER, SCROLL_FORWARD, GLOBAL_BACK.
            Each item may include: type, text, packageName, durationMs, isSensitive, spokenSummary.
            Return JSON only.

            Screen:
            $screenContext

            User request:
            $prompt
        """.trimIndent()

        val raw = runCatching { inference.generateResponse(plannerPrompt) }.getOrElse { error ->
            Log.e("AIBrain", "Gemma inference failed: ${error.message}", error)
            return emptyList()
        }
        return parseActions(raw)
    }

    private fun parseActions(raw: String): List<Action> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            val array = JSONArray(raw.substring(start, end + 1))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val type = runCatching { ActionType.valueOf(item.getString("type")) }.getOrNull() ?: continue
                    add(
                        Action(
                            type = type,
                            text = item.optNullableString("text"),
                            packageName = item.optNullableString("packageName"),
                            durationMs = item.optLong("durationMs", 800L),
                            isSensitive = item.optBoolean("isSensitive", false),
                            spokenSummary = item.optString("spokenSummary", type.name)
                        )
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun org.json.JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }
}
