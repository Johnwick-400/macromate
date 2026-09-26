package com.example.data.repository

import org.json.JSONObject

/**
 * Picks the language model behind the ReAct agent's non-deterministic steps (planning thought,
 * retry suggestion, final narration). An installed on-device Qwen model always wins and is never
 * silently replaced by a cloud call; cloud Gemini is only used when no local model is installed
 * and a key is configured; otherwise the agent uses its built-in templates.
 */
object AgentLlm {
    enum class Mode { LOCAL, CLOUD, NONE }

    fun mode(): Mode = when {
        QwenLlm.isInstalled() -> Mode.LOCAL
        RestGeminiClient.isApiKeyAvailable() -> Mode.CLOUD
        else -> Mode.NONE
    }

    fun label(): String = when (mode()) {
        Mode.LOCAL -> "Qwen 2.5 (on-device)"
        Mode.CLOUD -> "Gemini (cloud)"
        Mode.NONE -> "offline templates"
    }

    suspend fun generateJson(prompt: String, systemInstruction: String): JSONObject? = when (mode()) {
        Mode.LOCAL -> QwenLlm.generateJson(prompt, systemInstruction)
        Mode.CLOUD -> RestGeminiClient.generateJson(prompt = prompt, systemInstruction = systemInstruction)
        Mode.NONE -> null
    }
}
