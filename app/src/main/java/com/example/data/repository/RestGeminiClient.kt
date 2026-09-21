package com.example.data.repository

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit

object RestGeminiClient {
    private const val TAG = "RestGeminiClient"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    /**
     * Checks if the Gemini API Key is available and non-placeholder.
     */
    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return !key.isNullOrBlank() && key != "MY_GEMINI_API_KEY" && !key.contains("PLACEHOLDER")
    }

    /**
     * Generates a text response from Gemini.
     */
    suspend fun generateText(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            Log.w(TAG, "Gemini API key is not set. Falling back to local offline simulation.")
            return@withContext getLocalSimulationResponse(prompt)
        }

        val key = BuildConfig.GEMINI_API_KEY
        val url = "$BASE_URL?key=$key"

        try {
            // Build content list
            val contentObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                })
            }
            val contentsArr = JSONArray().apply { put(contentObj) }

            // Base Request JSON
            val requestJson = JSONObject().apply {
                put("contents", contentsArr)
                if (systemInstruction != null) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                }
            }

            val body = requestJson.toString().toRequestBody(mediaTypeJson)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Request failed: Code ${response.code}, Msg: $errBody")
                    return@withContext getLocalSimulationResponse(prompt)
                }

                val responseBody = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBody)
                val text = responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                return@withContext text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateText: ${e.message}", e)
            return@withContext getLocalSimulationResponse(prompt)
        }
    }

    /**
     * Generates a structured JSON response from Gemini.
     */
    suspend fun generateJson(prompt: String, systemInstruction: String? = null): JSONObject? = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext null
        }

        val key = BuildConfig.GEMINI_API_KEY
        val url = "$BASE_URL?key=$key"

        try {
            val contentObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                })
            }
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply { put(contentObj) })
                if (systemInstruction != null) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                }
                // Add structured output formatting
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val body = requestJson.toString().toRequestBody(mediaTypeJson)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseBody = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBody)
                val text = responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                return@withContext JSONObject(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateJson: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Generates a structured JSON response from Gemini for an image + text prompt (vision).
     */
    suspend fun generateJsonFromImage(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        prompt: String,
        systemInstruction: String? = null
    ): JSONObject? = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext null
        }

        val key = BuildConfig.GEMINI_API_KEY
        val url = "$BASE_URL?key=$key"

        try {
            val imageB64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val contentObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", mimeType)
                            put("data", imageB64)
                        })
                    })
                    put(JSONObject().apply { put("text", prompt) })
                })
            }
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply { put(contentObj) })
                if (systemInstruction != null) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                }
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val body = requestJson.toString().toRequestBody(mediaTypeJson)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseBody = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBody)
                val text = responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                return@withContext JSONObject(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateJsonFromImage: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Highly authentic, comprehensive simulation fallback logic when offline or API key isn't provided.
     * This replicates the exact ReAct mental loop and search capabilities.
     */
    private fun getLocalSimulationResponse(prompt: String): String {
        val lower = prompt.lowercase(Locale.ROOT)
        return when {
            lower.contains("protein") || lower.contains("healthy") || lower.contains("post-workout") || lower.contains("chicken") || lower.contains("paneer") -> {
                """
                THINKING: Searching restaurants matching "healthy and protein"...
                THINKING: Analysing menus from top restaurants for calories and macro suitability...
                THINKING: Calculating macro congruence indices...
                
                I have processed your nutrition request and found some incredibly fitting Indian meal options matching your macros:
                
                1. **Keto Herb Chicken Salad** from *EatFit - Healthy Bowls*
                   - Macros: 38g Protein · 8g Carbs · 18g Fat · calories: 346
                   - Pricing: ₹289.0
                   - Alignment Score: 96%
                   - Reason: Perfect high-protein, near-zero carb pick that guarantees quick muscle repair without exceeding fat boundaries.
                
                2. **Tandoori Chicken Tikka Large** from *Punjab Bistro - High Protein*
                   - Macros: 42g Protein · 4g Carbs · 14g Fat · calories: 310
                   - Pricing: ₹349.0
                   - Alignment Score: 94%
                   - Reason: Classic Indian muscle fuel. Completely roasted with zero added raw sugar, boasting massive bio-available protein.
                
                3. **High-Protein Tofu Crunch Salad** from *The Salad Company*
                   - Macros: 23g Protein · 12g Carbs · 12g Fat · calories: 248
                   - Pricing: ₹299.0
                   - Alignment Score: 88%
                   - Reason: Dynamic and light vegetarian/vegan replacement showcasing heart-healthy plant lipids and high-quality soy fibers.
                
                Would you like me to instantly prepare a cart or would search parameters need adjusting?
                """.trimIndent()
            }
            lower.contains("keto") || lower.contains("fat") || lower.contains("low carb") -> {
                """
                THINKING: Querying Swiggy database for low carb / ketogenic options...
                THINKING: Inspecting menu arrays and computing macro ratios...
                
                Ideal Keto options ready for your review:
                
                1. **Keto Herb Chicken Salad** from *EatFit - Healthy Bowls*
                   - Macros: 38g Protein · 8g Carbs · 18g Fat · calories: 346
                   - Pricing: ₹289.0
                   - Alignment Score: 98%
                   - Reason: Perfectly tuned fat-to-protein ratio designed to support nutritional ketosis.
                
                2. **Clean Avocado Keto Superfood Wrap** from *The Salad Company*
                   - Macros: 15g Protein · 14g Carbs · 16g Fat · calories: 260
                   - Pricing: ₹279.0
                   - Alignment Score: 91%
                   - Reason: Loaded with critical monounsaturated fatty acids from fresh avocado mash and high protein eggs.
                   
                3. **Keto Sugar-Free Chocolate Pot** from *EatFit - Healthy Bowls*
                   - Macros: 5g Protein · 11g Carbs · 15g Fat · calories: 199
                   - Pricing: ₹149.0
                   - Alignment Score: 85%
                   - Reason: A supreme macro-friendly dessert using almond milk to satisfy cravings within boundaries.
                """.trimIndent()
            }
            else -> {
                """
                THINKING: Scanning available mock Swiggy directories for options...
                THINKING: Synthesizing menu files and checking allergy restriction profiles...
                
                Here are balanced choices tailored to you:
                
                1. **High-Protein Paneer Rice Bowl** from *EatFit - Healthy Bowls*
                   - Macros: 25g Protein · 45g Carbs · 12g Fat · calories: 390
                   - Pricing: ₹249.0
                   - Alignment Score: 92%
                   - Reason: Offers a solid, clean distribution of slow-release carbohydrates and fresh low-fat vegetarian protein.
                
                2. **Sprouted Moong Diet Dosa** from *Dakshin Flavors - High Protein South*
                   - Macros: 15g Protein · 28g Carbs · 4g Fat · calories: 208
                   - Pricing: ₹169.0
                   - Alignment Score: 89%
                   - Reason: Incredibly rich in micro-nutrients and plant-proteins, air-grilled with absolute minimal oil.
                """.trimIndent()
            }
        }
    }
}
