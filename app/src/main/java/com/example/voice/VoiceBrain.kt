package com.example.voice

import android.util.Log

/** Snapshot of what Buddy knows about the user, used to make filler questions feel personal. */
data class BuddyContext(
    val name: String,
    val goal: String,
    val consumedCalories: Int,
    val remainingCalories: Int,
    val lastOrderItem: String?,
    val loggedMealsToday: Int
)

/**
 * Generates short "filler" small-talk lines that Buddy speaks WHILE the real meal search runs
 * in the background — masking network latency so the user never stares at a spinner.
 *
 * Two backends behind one interface, matching the app's fail-closed design:
 *  - [TemplatedVoiceBrain] — instant, offline, zero-dependency. Always available. The guaranteed baseline.
 *  - [QwenVoiceBrain]      — optional on-device Qwen2.5-0.5B (llama.cpp). Richer banter. If the native
 *                            lib / model isn't present it reports unavailable and the templated brain
 *                            is used instead — the demo never depends on it.
 */
interface VoiceBrain {
    /** True only when this backend can actually produce output right now. */
    suspend fun isAvailable(): Boolean

    /** Returns one filler line given context and the questions already asked (to avoid repeats). */
    suspend fun nextFiller(ctx: BuddyContext, alreadyAsked: List<String>): String
}

/**
 * Deterministic, offline filler from profile + history. No model, no network → cannot fail,
 * adds no latency. This is the default and the fallback.
 */
class TemplatedVoiceBrain : VoiceBrain {
    override suspend fun isAvailable() = true

    override suspend fun nextFiller(ctx: BuddyContext, alreadyAsked: List<String>): String {
        val pool = buildList {
            add("Hey ${ctx.name}, hunting for the best matches now — how's your day going?")
            add("You're on a ${ctx.goal} plan — did you get a workout in today?")
            if (ctx.lastOrderItem != null) add("Last time you enjoyed the ${ctx.lastOrderItem}. Feeling something similar?")
            if (ctx.loggedMealsToday > 0) add("You've logged ${ctx.consumedCalories} kcal so far — busy one?")
            add("While I scan the menus — what do you do for work, if you don't mind me asking?")
            add("You've got about ${ctx.remainingCalories} kcal left today. Saving room for dessert? 😄")
            add("Almost there — anything you're specifically in the mood for, spicy or light?")
        }
        // First line not yet asked; wrap around if all used.
        return pool.firstOrNull { it !in alreadyAsked } ?: pool.last()
    }
}

/**
 * Pluggable on-device Qwen backend. Intentionally reports unavailable until the native llama.cpp
 * library and the quantized model asset are bundled — [VoiceBrainProvider] then falls back to
 * [TemplatedVoiceBrain] automatically. Wiring the native runtime is a drop-in step that touches
 * nothing else in the app.
 */
class QwenVoiceBrain : VoiceBrain {
    override suspend fun isAvailable(): Boolean {
        // Activation point: return true once the native runtime + model are present and loaded.
        return false
    }

    override suspend fun nextFiller(ctx: BuddyContext, alreadyAsked: List<String>): String {
        // Reached only if isAvailable() returns true. Until then, provider never calls this.
        Log.w("QwenVoiceBrain", "nextFiller called but Qwen runtime not wired; using templated text")
        return TemplatedVoiceBrain().nextFiller(ctx, alreadyAsked)
    }
}

/**
 * Picks the best available brain each session: Qwen if it's actually loaded, else templated.
 * The choice is resolved lazily so a future Qwen activation needs no change here.
 */
object VoiceBrainProvider {
    private val templated = TemplatedVoiceBrain()
    private val qwen = QwenVoiceBrain()

    suspend fun resolve(): VoiceBrain = if (qwen.isAvailable()) qwen else templated
}
