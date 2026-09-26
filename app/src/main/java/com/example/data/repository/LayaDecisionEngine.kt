package com.example.data.repository

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.data.model.BudgetTier
import com.example.data.model.MealGoalType
import com.example.data.model.SystemOneDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import java.util.Locale

/**
 * On-device "System 1" decision engine: a single forward pass through a quantized MiniLM
 * classifier (laya_decision.onnx) that turns a chat query directly into typed decisions —
 * [MealGoalType], [BudgetTier], and a vegetarian flag — with no free-text generation, so
 * there's no schema to hallucinate.
 *
 * Fails closed: if the model/vocab assets aren't bundled, or inference throws for any reason,
 * [evaluateIntent] transparently answers via a deterministic keyword-rule fallback instead of
 * crashing or blocking. [SystemOneDecision.usedOnnxModel] tells the caller which path answered.
 */
object LayaDecisionEngine {
    private const val TAG = "LayaDecisionEngine"
    private const val MODEL_ASSET = "laya_decision.onnx"
    private const val VOCAB_ASSET = "vocab.txt"
    private const val MAX_SEQ_LEN = 64

    // Index order must match the training script's label encoding exactly (build_laya_model.py).
    private val GOAL_ORDER = arrayOf(
        MealGoalType.HIGH_PROTEIN,
        MealGoalType.KETO_FAT_LOSS,
        MealGoalType.LEAN_MASS_BULK,
        MealGoalType.LOW_CALORIE_CLEAN,
        MealGoalType.QUICK_HYDRATION_SNACK
    )
    private val BUDGET_ORDER = arrayOf(
        BudgetTier.BUDGET_UNDER_200,
        BudgetTier.STANDARD_UNDER_400,
        BudgetTier.PREMIUM_UNRESTRICTED
    )

    private lateinit var appContext: Context
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    @Volatile private var session: OrtSession? = null
    @Volatile private var tokenizer: WordPieceTokenizer? = null
    @Volatile private var loadAttempted = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Lazy, one-shot, never-throws load of the model + vocab. Leaves [session] null on any failure. */
    private fun ensureLoaded() {
        if (loadAttempted) return
        loadAttempted = true
        try {
            val vocabText = appContext.assets.open(VOCAB_ASSET).bufferedReader().use { it.readText() }
            val loadedTokenizer = WordPieceTokenizer(vocabText)

            val modelBytes = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
            val options = OrtSession.SessionOptions()
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            try {
                options.addNnapi()
            } catch (e: Throwable) {
                Log.w(TAG, "NNAPI execution provider unavailable — falling back to multi-threaded CPU", e)
                options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
            }

            session = env.createSession(modelBytes, options)
            tokenizer = loadedTokenizer
            Log.i(TAG, "Laya decision model loaded: ${modelBytes.size} bytes, ${loadedTokenizer.vocabSize} vocab tokens")
        } catch (e: Throwable) {
            // Broad on purpose: this must also catch UnsatisfiedLinkError (e.g. no native
            // onnxruntime .so available, such as under a plain-JVM Robolectric unit test), not
            // just Exception — any failure here means "use the rule-based fallback", never crash.
            Log.w(TAG, "laya_decision.onnx/vocab.txt not usable — System 1 engine will use the rule-based fallback", e)
            session = null
            tokenizer = null
        }
    }

    suspend fun evaluateIntent(query: String, remainingProtein: Int, remainingCalories: Int): SystemOneDecision =
        withContext(Dispatchers.Default) {
            ensureLoaded()
            val activeSession = session
            val activeTokenizer = tokenizer
            if (activeSession == null || activeTokenizer == null) {
                return@withContext ruleBasedFallback(query, remainingProtein, remainingCalories)
            }
            try {
                runOnnxInference(activeSession, activeTokenizer, query)
            } catch (e: Throwable) {
                Log.w(TAG, "Laya inference failed — falling back to rules for this query", e)
                ruleBasedFallback(query, remainingProtein, remainingCalories)
            }
        }

    private fun runOnnxInference(session: OrtSession, tokenizer: WordPieceTokenizer, query: String): SystemOneDecision {
        val start = System.nanoTime()
        val (ids, mask) = tokenizer.encode(query, MAX_SEQ_LEN)
        val shape = longArrayOf(1, ids.size.toLong())

        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { idsTensor ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape).use { maskTensor ->
                val inputs = mapOf("input_ids" to idsTensor, "attention_mask" to maskTensor)
                session.run(inputs).use { result ->
                    // Positional access — graph output order is fixed at export time
                    // (goal_logits, budget_logits, veg_logits; verified against the .onnx graph).
                    @Suppress("UNCHECKED_CAST")
                    val goalLogits = (result[0].value as Array<FloatArray>)[0]
                    @Suppress("UNCHECKED_CAST")
                    val budgetLogits = (result[1].value as Array<FloatArray>)[0]
                    @Suppress("UNCHECKED_CAST")
                    val vegLogits = (result[2].value as Array<FloatArray>)[0]

                    val goalProbs = softmax(goalLogits)
                    val budgetProbs = softmax(budgetLogits)
                    val vegProbs = softmax(vegLogits)

                    val goalIdx = goalProbs.indices.maxByOrNull { goalProbs[it] } ?: 0
                    val budgetIdx = budgetProbs.indices.maxByOrNull { budgetProbs[it] } ?: 1
                    val isVeg = vegIntent(query, vegProbs[1])
                    val goal = GOAL_ORDER[goalIdx]

                    return SystemOneDecision(
                        goal = goal,
                        budget = budgetIntent(query, BUDGET_ORDER[budgetIdx]),
                        searchKeyword = extractFoodTerm(query) ?: searchKeywordFor(goal, isVeg),
                        requestedFood = extractFoodTerm(query),
                        isVegetarianForced = isVeg,
                        confidence = goalProbs[goalIdx],
                        latencyMs = (System.nanoTime() - start) / 1_000_000,
                        usedOnnxModel = true
                    )
                }
            }
        }
    }

    private val NON_VEG_WORDS = listOf("chicken", "mutton", "fish", "egg", "prawn", "non-veg", "nonveg", "meat", "biryani")
    private val VEG_HINTS = listOf(
        "veg", "paneer", "vegan", "tofu", "soya", "dal", "sprout", "khichdi", "chaas", "buttermilk", "lassi",
        "juice", "salad", "fruit", "idli", "dosa", "upma"
    )

    /**
     * Whether to restrict results to vegetarian food. An explicit word in the query decides; the
     * model's guess only counts when it is very confident, because it has no signal for plain
     * requests like "burgers" and should not silently exclude chicken options.
     */
    private fun vegIntent(query: String, modelVegProbability: Float): Boolean {
        val lower = query.lowercase(Locale.ROOT)
        if (NON_VEG_WORDS.any { it in lower }) return false
        if (VEG_HINTS.any { it in lower }) return true
        return modelVegProbability >= 0.9f
    }

    private val PRICE_SIGNAL = Regex("\\d|cheap|budget|afford|premium|luxury|expens|pocket|price|cost|rupee|\\brs\\b|₹")

    /**
     * A price ceiling is only applied when the query talks about price. The model has no signal for
     * a plain "burgers", so its guess ("under ₹200") must not silently hide restaurants.
     */
    private fun budgetIntent(query: String, modelPick: BudgetTier): BudgetTier =
        if (PRICE_SIGNAL.containsMatchIn(query.lowercase(Locale.ROOT))) modelPick else BudgetTier.PREMIUM_UNRESTRICTED

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = FloatArray(logits.size) { kotlin.math.exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return FloatArray(logits.size) { exps[it] / sum }
    }

    /** Search keyword for a goal when the user didn't name a specific food. */
    fun goalKeyword(goal: MealGoalType, isVeg: Boolean): String = searchKeywordFor(goal, isVeg)

    private fun searchKeywordFor(goal: MealGoalType, isVeg: Boolean): String {
        val base = when (goal) {
            MealGoalType.HIGH_PROTEIN -> "high protein"
            MealGoalType.KETO_FAT_LOSS -> "keto"
            MealGoalType.LEAN_MASS_BULK -> "high calorie"
            MealGoalType.LOW_CALORIE_CLEAN -> "healthy low calorie"
            MealGoalType.QUICK_HYDRATION_SNACK -> "juice"
        }
        return if (isVeg) "vegetarian $base" else base
    }

    // ponytail: keyword rules mirroring the training set's phrasing (build_laya_model.py) —
    // only used when the ONNX model/vocab isn't bundled or inference throws, so the "System 1"
    // step always answers deterministically and never blocks or crashes the ReAct loop.
    private fun ruleBasedFallback(query: String, remainingProtein: Int, remainingCalories: Int): SystemOneDecision {
        val start = System.nanoTime()
        val lower = query.lowercase(Locale.ROOT)

        val goal = when {
            listOf("keto", "low carb", "no carb").any { it in lower } -> MealGoalType.KETO_FAT_LOSS
            listOf("bulk", "mass gain", "mass builder", "gainer").any { it in lower } -> MealGoalType.LEAN_MASS_BULK
            listOf("juice", "hydration", "coconut water", "buttermilk", "lassi", "energy boost", "cold pressed").any { it in lower } -> MealGoalType.QUICK_HYDRATION_SNACK
            listOf("low calorie", "diet", "light meal", "salad", "weight loss", "fat loss").any { it in lower } -> MealGoalType.LOW_CALORIE_CLEAN
            else -> MealGoalType.HIGH_PROTEIN
        }

        val budgetAmount = Regex("under\\s*(?:rs\\.?|₹)?\\s*(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d+)\\s*(?:rupees|rs\\b)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        val budget = when {
            budgetAmount != null && budgetAmount <= 200 -> BudgetTier.BUDGET_UNDER_200
            budgetAmount != null && budgetAmount <= 400 -> BudgetTier.STANDARD_UNDER_400
            budgetAmount != null -> BudgetTier.PREMIUM_UNRESTRICTED
            listOf("cheap", "budget", "affordable").any { it in lower } -> BudgetTier.BUDGET_UNDER_200
            listOf("premium", "luxury", "unrestricted").any { it in lower } -> BudgetTier.PREMIUM_UNRESTRICTED
            else -> BudgetTier.PREMIUM_UNRESTRICTED // no price words: don't invent a ceiling
        }

        val nonVegWords = listOf("chicken", "mutton", "fish", "egg", "prawn", "non-veg", "nonveg", "meat", "biryani")
        val vegWords = listOf("veg", "paneer", "vegan", "tofu", "sprouts", "dal", "soya")
        val isVeg = nonVegWords.none { it in lower } && vegWords.any { it in lower }

        return SystemOneDecision(
            goal = goal,
            budget = budget,
            searchKeyword = extractFoodTerm(query) ?: searchKeywordFor(goal, isVeg),
            requestedFood = extractFoodTerm(query),
            isVegetarianForced = isVeg,
            confidence = 0.6f,
            latencyMs = (System.nanoTime() - start) / 1_000_000,
            usedOnnxModel = false
        )
    }
}

/**
 * Minimal, standard BERT WordPiece tokenizer (greedy longest-match-first), loading its
 * vocabulary from the bundled vocab.txt at runtime rather than hardcoding it — this exact
 * scheme is what sentence-transformers/all-MiniLM-L6-v2 (the model's base encoder) expects.
 */
private class WordPieceTokenizer(vocabText: String) {
    private val vocab: Map<String, Int> = vocabText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .withIndex()
        .associate { (idx, token) -> token to idx }

    val vocabSize: Int get() = vocab.size

    private val clsId = vocab["[CLS]"] ?: 101
    private val sepId = vocab["[SEP]"] ?: 102
    private val unkId = vocab["[UNK]"] ?: 100

    /** Returns (input_ids, attention_mask) as equal-length LongArrays, [CLS]-wrapped-[SEP]. */
    fun encode(text: String, maxLen: Int): Pair<LongArray, LongArray> {
        val ids = mutableListOf(clsId)
        for (word in basicTokenize(text)) {
            if (ids.size >= maxLen - 1) break
            ids.addAll(wordPiece(word))
        }
        if (ids.size > maxLen - 1) {
            while (ids.size > maxLen - 1) ids.removeAt(ids.size - 1)
        }
        ids.add(sepId)

        val idsArr = LongArray(ids.size) { ids[it].toLong() }
        val mask = LongArray(ids.size) { 1L }
        return idsArr to mask
    }

    /** Lowercases and splits into words + standalone punctuation tokens (BERT's BasicTokenizer). */
    private fun basicTokenize(text: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        for (c in text.lowercase(Locale.ROOT)) {
            when {
                c.isWhitespace() -> {
                    if (current.isNotEmpty()) { words.add(current.toString()); current.clear() }
                }
                c.isLetterOrDigit() -> current.append(c)
                else -> {
                    if (current.isNotEmpty()) { words.add(current.toString()); current.clear() }
                    words.add(c.toString())
                }
            }
        }
        if (current.isNotEmpty()) words.add(current.toString())
        return words
    }

    /** Greedy longest-match-first subword split; a single [UNK] if no valid split exists. */
    private fun wordPiece(word: String): List<Int> {
        if (word.length > 100) return listOf(unkId)
        val ids = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var matchedId: Int? = null
            while (end > start) {
                val piece = if (start == 0) word.substring(start, end) else "##" + word.substring(start, end)
                val id = vocab[piece]
                if (id != null) { matchedId = id; break }
                end--
            }
            if (matchedId == null) return listOf(unkId)
            ids.add(matchedId)
            start = end
        }
        return ids
    }
}


private val NON_FOOD_WORDS = setOf(
    // conversational filler
    "want", "need", "looking", "find", "get", "order", "please", "pls", "plz", "suggest", "show", "give", "some", "any",
    "with", "and", "for", "the", "can", "could", "would", "you", "have", "that", "this", "what", "which", "like", "love",
    "really", "very", "just", "only", "maybe", "hungry", "craving", "cravings", "crave", "feel", "feeling", "options",
    "option", "dishes", "dish", "recipes", "items", "item", "place", "places", "restaurant", "restaurants", "nearby",
    "near", "around", "here", "there", "today", "tomorrow", "tonight", "morning", "evening", "afternoon", "night",
    // budget
    "under", "below", "above", "over", "than", "less", "more", "upto", "within", "about", "rupees", "rupee", "inr",
    "budget", "cheap", "affordable", "premium", "luxury", "expensive", "price", "cost",
    // goals and nutrition
    "high", "low", "protein", "carb", "carbs", "fat", "fats", "calorie", "calories", "cal", "kcal", "keto", "diet",
    "healthy", "health", "light", "clean", "bulk", "mass", "gain", "lean", "fit", "fitness", "macro", "macros", "sugar",
    "zero", "free", "gluten", "dairy", "nut", "allergy",
    // diet
    "veg", "vegetarian", "vegan", "nonveg", "non", "meat",
    "weight", "loss", "lose", "losing", "cut", "cutting", "muscle", "build", "building", "gaining",
    // cooking words describe the dish, they aren't its name
    "boiled", "grilled", "fried", "steamed", "roasted", "baked", "crispy", "spicy", "creamy", "cheesy",
    // meal words
    "meal", "meals", "food", "lunch", "dinner", "breakfast", "brunch", "snack", "snacks", "post", "pre", "workout", "gym",
    // vague adjectives
    "good", "best", "tasty", "delicious", "hot", "cold", "fresh", "quick", "fast", "small", "big", "large", "double",
    "extra", "something", "anything", "nice", "great"
)

/**
 * The specific food the user named, if any: what is left of the sentence once filler, budget,
 * goal, diet and meal-time words are removed ("burgers" -> "burger", "chicken burger under 250" ->
 * "chicken burger"). Null when the request is only about goals ("high protein lunch under 300").
 */
internal fun extractFoodTerm(query: String): String? {
    val words = query.lowercase(Locale.ROOT)
        .split(Regex("[^a-z]+"))
        .filter { it.length >= 3 && it !in NON_FOOD_WORDS }
        .map { singular(it) }
    return words.take(2).joinToString(" ").ifBlank { null }
}

private fun singular(word: String): String = when {
    word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
    word.length > 4 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is") -> word.dropLast(1)
    else -> word
}
