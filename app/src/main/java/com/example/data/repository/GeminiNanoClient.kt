package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Structured macro-estimation output, requested directly from Gemini Nano via the
 *  Structured Output API — no manual JSON parsing needed on this path. */
@Generable(description = "Nutrition estimate for a single food item or plate")
data class NanoFoodEstimate(
    @param:Guide(description = "Short name of the dish/food identified, or null if not identifiable")
    val foodName: String?,
    @param:Guide(description = "Estimated total calories (kcal) for the visible portion")
    val calories: Int,
    @param:Guide(description = "Estimated protein in grams")
    val proteinG: Int,
    @param:Guide(description = "Estimated carbohydrates in grams")
    val carbsG: Int,
    @param:Guide(description = "Estimated fat in grams")
    val fatG: Int,
)

/**
 * On-device macro estimation via Gemini Nano (Android AICore, ML Kit GenAI Prompt API).
 * Requires API 26+ and a device where AICore actually ships Gemini Nano — availability is
 * never assumed, always checked. Every entry point fails closed to null on any error, missing
 * model, or unsupported device, matching the rest of this app's AI-optional design: callers
 * fall back to [RestGeminiClient] (cloud) and, beyond that, to manual entry.
 */
object GeminiNanoClient {
    private const val TAG = "GeminiNanoClient"

    private val model: GenerativeModel by lazy { Generation.getClient() }

    // Cached for the process lifetime — checkStatus()/download() don't need to repeat per call.
    @Volatile private var availableCache: Boolean? = null

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        availableCache?.let { return@withContext it }
        val result = try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> true
                FeatureStatus.DOWNLOADABLE -> {
                    // ponytail: blocks on the on-device model download the first time this
                    // runs on a supported device; acceptable since it's a one-time cost and
                    // every caller already has a "still estimating..." loading state.
                    model.download().first {
                        it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
                    } is DownloadStatus.DownloadCompleted
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano availability check failed", e)
            false
        }
        availableCache = result
        result
    }

    suspend fun estimateMacrosFromText(prompt: String): NanoFoodEstimate? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        try {
            val request = generateTypedContentRequest(
                generateContentRequest(TextPart(prompt)) {},
                NanoFoodEstimate::class,
            )
            model.generateContent(request).candidates.firstOrNull()?.response
        } catch (e: Exception) {
            Log.w(TAG, "On-device text macro estimation failed", e)
            null
        }
    }

    suspend fun estimateMacrosFromImage(bitmap: Bitmap, prompt: String): NanoFoodEstimate? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        try {
            val request = generateTypedContentRequest(
                generateContentRequest(ImagePart(bitmap), TextPart(prompt)) {},
                NanoFoodEstimate::class,
            )
            model.generateContent(request).candidates.firstOrNull()?.response
        } catch (e: Exception) {
            Log.w(TAG, "On-device photo macro estimation failed", e)
            null
        }
    }
}
