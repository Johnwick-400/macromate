package com.example.data.repository

import android.util.Log
import com.example.data.model.BudgetTier
import com.example.data.model.DishItem
import com.example.data.model.SystemOneDecision
import com.example.data.model.Restaurant
import com.example.data.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

sealed class AgentState {
    data class Thinking(val step: String) : AgentState()
    data class Success(val message: String, val results: List<DishItem>) : AgentState()
    data class Error(val message: String) : AgentState()
}

object MacroMateAgent {
    private const val TAG = "MacroMateAgent"

    /**
     * Executes the ReAct agent loop:
     * 1. (agentic, if Gemini available) interpret the raw query into a concrete search term
     * 2. TOOL 1+2: searchRestaurants + getMenuAndEstimateMacros, filter allergens/diet, score
     * 3. (agentic, if Gemini available) if too few good matches, ask for a different search
     *    angle and retry once — bounded so latency/cost stay predictable
     * 4. Narrate the final results
     *
     * The macro scoring and dietary filtering in [runSearchPass] stay fully deterministic —
     * that math is correctness-critical (verified in MacroMateAgentScoringTest) and isn't
     * handed to the LLM. Only *which* search term to try is agentic.
     */
    fun searchAndRankMeals(
        query: String,
        profile: UserProfile,
        remainingCalories: Int,
        remainingProtein: Int,
        remainingCarbs: Int,
        remainingFat: Int
    ): Flow<AgentState> = flow {
        try {
            // Own short step so the source isn't ellipsized away on narrow screens
            emit(AgentState.Thinking(if (SwiggyClient.isLive()) "Data source: live Swiggy MCP" else "Data source: offline demo restaurants"))

            val allergensList = parseList(profile.allergiesJson).map { it.lowercase(Locale.ROOT).trim() }
            val cuisinePrefs = parseList(profile.cuisinePrefsJson).map { it.lowercase(Locale.ROOT).trim() }
            Log.d(TAG, "Allergen list for filtering: $allergensList")
            Log.d(TAG, "Cuisine preferences: $cuisinePrefs")

            val decision = LayaDecisionEngine.evaluateIntent(query, remainingProtein, remainingCalories)
            val enginePath = if (decision.usedOnnxModel) "On-Device NPU/CPU" else "rule fallback"
            emit(AgentState.Thinking(
                "⚡ System 1 Decision (Laya · $enginePath · ${decision.latencyMs}ms): " +
                        "Goal=${decision.goal}, Budget=${decision.budget}"
            ))
            val searchTerm = decision.searchKeyword
            val forceVeg = decision.isVegetarianForced
            val foodTerm = decision.requestedFood
            val stats = SearchStats()
            if (foodTerm != null) emit(AgentState.Thinking("🔎 You asked for \"$foodTerm\" — searching for that first."))

            if (AgentLlm.mode() == AgentLlm.Mode.LOCAL) {
                emit(AgentState.Thinking(
                    if (QwenLlm.state.value.phase == QwenPhase.READY) "🧠 Qwen 2.5 (on-device) is planning..."
                    else "🧠 Loading Qwen 2.5 on-device (first use takes a little while)..."
                ))
                planThought(query, profile, decision, remainingCalories, remainingProtein)
                    ?.let { emit(AgentState.Thinking("💭 $it")) }
            }

            var matchingDishes = runSearchPass(
                searchTerm, profile, remainingCalories, remainingProtein, remainingCarbs, remainingFat,
                allergensList, cuisinePrefs, decision.budget, forceVeg,
                strict = foodTerm != null, foodTerm = foodTerm, stats = stats
            ) { emit(it) }

            // If a specific food was requested, only dishes that really are that food count. When there
            // are none, say why and fall back to the user's goal instead of silently showing something else.
            var noFoodReason: String? = null
            if (foodTerm != null) {
                val exact = matchingDishes.filter { dishMatchesFood(it, foodTerm) }
                if (exact.isNotEmpty()) {
                    matchingDishes = exact
                } else {
                    noFoodReason = if (stats.matchingSeen > 0)
                        "I found \"$foodTerm\" options, but none fit your dietary or allergy settings"
                    else
                        "I couldn't find \"$foodTerm\" on the menus I can see right now"
                    emit(AgentState.Thinking("No usable \"$foodTerm\" dishes — falling back to your ${decision.goal} goal..."))
                    matchingDishes = runSearchPass(
                        LayaDecisionEngine.goalKeyword(decision.goal, forceVeg),
                        profile, remainingCalories, remainingProtein, remainingCarbs, remainingFat,
                        allergensList, cuisinePrefs, decision.budget, forceVeg
                    ) { emit(it) }
                }
            }

            // Agentic retry: one bounded re-search from a different angle if results are weak
            // A named food that was found is what the user wants: don't dilute it with the "weak results" retry.
            if (needsRetry(matchingDishes) && AgentLlm.mode() != AgentLlm.Mode.NONE &&
                (foodTerm == null || noFoodReason != null)) {
                val altTerm = suggestAlternativeSearch(query, searchTerm, profile)
                if (altTerm != null && !altTerm.equals(searchTerm, ignoreCase = true)) {
                    emit(AgentState.Thinking("Only ${matchingDishes.size} strong matches for \"$searchTerm\" — asking ${AgentLlm.label()} for a different angle..."))
                    delay(400)
                    emit(AgentState.Thinking("Retrying with refined query: \"$altTerm\""))
                    val retryDishes = runSearchPass(
                        altTerm, profile, remainingCalories, remainingProtein, remainingCarbs, remainingFat,
                        allergensList, cuisinePrefs, decision.budget, forceVeg
                    ) { emit(it) }
                    matchingDishes = (matchingDishes + retryDishes).distinctBy { it.id }
                }
            }

            emit(AgentState.Thinking("Evaluating macro score vectors & compiling optimal nutrition list..."))
            delay(1000)

            // Sort dishes by macro score descending
            val sortedDishes = matchingDishes.sortedByDescending { it.score }.take(5)

            if (sortedDishes.isEmpty()) {
                emit(AgentState.Success(
                    (noFoodReason?.let { "$it, and nothing else " } ?: "No meals ") +
                        "fits your macro and dietary requirements at this time.",
                    emptyList()
                ))
            } else {
                var responseMsg = if (noFoodReason != null)
                    "Here are the top ${sortedDishes.size} closest matches for your macro and budget targets."
                else
                    "I have successfully compiled the top ${sortedDishes.size} Swiggy dishes matching your strict target goals!"
                var finalDishes = sortedDishes

                if (AgentLlm.mode() != AgentLlm.Mode.NONE) {
                    emit(AgentState.Thinking("Drafting recommendations with ${AgentLlm.label()}..."))
                    generateLlmResponse(query, profile, remainingCalories, remainingProtein, sortedDishes, noFoodReason)?.let { (msg, dishes) ->
                        responseMsg = msg
                        finalDishes = dishes
                    }
                }
                if (noFoodReason != null) responseMsg = "$noFoodReason. $responseMsg"
                emit(AgentState.Success(responseMsg, finalDishes))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error compiling agent response", e)
            emit(AgentState.Error("An error occurred during ReAct Agent evaluation: ${e.localizedMessage}"))
        }
    }

    /** Counts real matches for the requested food that were seen before dietary filters removed any. */
    internal class SearchStats { var matchingSeen = 0 }

    /** True when every word of [term] appears in the dish's name or description. */
    internal fun dishMatchesFood(dish: DishItem, term: String): Boolean {
        val text = "${dish.name} ${dish.description}".lowercase(Locale.ROOT)
        val words = term.lowercase(Locale.ROOT).split(' ').filter { it.length >= 3 }
        return words.isNotEmpty() && words.all { it in text }
    }

    /** True once fewer than 2 dishes clear a reasonable macro-fit bar — worth trying a different search angle. */
    internal fun needsRetry(dishes: List<DishItem>): Boolean = dishes.count { it.score > 0.4 } < 2

    /**
     * One search+menu+filter+score pass against [SwiggyClient]. Emits its own Thinking steps
     * via [emit] so it can be invoked more than once (initial attempt + agentic retry) from
     * inside the same flow builder.
     */
    private suspend fun runSearchPass(
        searchTerm: String,
        profile: UserProfile,
        remainingCalories: Int,
        remainingProtein: Int,
        remainingCarbs: Int,
        remainingFat: Int,
        allergensList: List<String>,
        cuisinePrefs: List<String>,
        budget: BudgetTier,
        forceVeg: Boolean,
        strict: Boolean = false,
        foodTerm: String? = null,
        stats: SearchStats? = null,
        emit: suspend (AgentState) -> Unit
    ): List<DishItem> {
        emit(AgentState.Thinking("Calling Tool: searchRestaurants(query=\"$searchTerm\", budget=$budget)"))
        delay(1200)

        val restaurants = SwiggyClient.searchRestaurants(query = searchTerm, budget = budget, strict = strict)
        emit(AgentState.Thinking("Found ${restaurants.size} restaurant candidates in your zone. Analysing menus..."))
        delay(1000)

        // Limit to top 3 restaurants as required
        val activeRestaurants = restaurants.take(3)
        emit(AgentState.Thinking("Calling Tool: getMenuAndEstimateMacros for top ${activeRestaurants.size} restaurants: " +
                activeRestaurants.joinToString { it.name }))
        delay(1200)

        val matchingDishes = mutableListOf<DishItem>()

        for (restaurant in activeRestaurants) {
            emit(AgentState.Thinking("Scanning menu for restaurant: ${restaurant.name}..."))
            delay(800)

            val menu = SwiggyClient.getMenu(restaurant.id)

            for (dish in menu) {
                if (foodTerm != null && stats != null && dishMatchesFood(dish, foodTerm)) stats.matchingSeen++

                // Check allergens
                if (containsAllergens(dish, allergensList)) {
                    Log.d(TAG, "Filtering out dish ${dish.name} because of allergy restrictions")
                    continue
                }

                // Check core dietary restrictions (Vegetarian, Vegan) — a profile-level
                // preference, or the System 1 engine reading "veg"/"paneer"/etc. in this query.
                if ((allergensList.contains("vegetarian") || forceVeg) && isNonVeg(dish)) {
                    Log.d(TAG, "Filtering out non-veg dish ${dish.name} for vegetarian preference")
                    continue
                }
                if (allergensList.contains("vegan") && !isVegan(dish)) {
                    Log.d(TAG, "Filtering out non-vegan dish ${dish.name} for vegan user")
                    continue
                }

                val baseScore = calculateMacroScore(
                    dish,
                    targetCalories = mealTarget(remainingCalories, profile.dailyCalories),
                    targetProtein = mealTarget(remainingProtein, profile.dailyProteinG),
                    targetCarbs = mealTarget(remainingCarbs, profile.dailyCarbsG),
                    targetFat = mealTarget(remainingFat, profile.dailyFatG),
                    targetPrice = restaurant.averagePrice
                )

                // Boost for preferred cuisine
                val cuisineMatch = restaurant.cuisines.any { rc ->
                    cuisinePrefs.any { cp -> rc.lowercase(Locale.ROOT).contains(cp) }
                }
                val finalScore = if (cuisineMatch) Math.min(1.0, baseScore + 0.05) else baseScore

                // AI Reasoning generator
                val aiReasoning = generateAIReasoning(dish, profile, finalScore)

                matchingDishes.add(
                    dish.copy(
                        score = finalScore,
                        aiReasoning = aiReasoning,
                        restaurantName = restaurant.name,
                        restaurantId = restaurant.id
                    )
                )
            }
        }
        return matchingDishes
    }

    /** One-sentence plan written by the on-device model — the "Thought" of this ReAct cycle. */
    private suspend fun planThought(
        query: String,
        profile: UserProfile,
        decision: SystemOneDecision,
        remainingCalories: Int,
        remainingProtein: Int
    ): String? = QwenLlm.generate(
        system = "You are the planning step of a food-ordering agent. Reply with exactly one sentence, at most 30 words, no preamble.",
        prompt = "User request: \"$query\". Goal: ${profile.goal}. Remaining today: ${remainingCalories} kcal, ${remainingProtein}g protein. " +
                "Fast classifier says: ${decision.goal}, budget ${decision.budget}, " +
                "${if (decision.isVegetarianForced) "vegetarian" else "any diet"}, search \"${decision.searchKeyword}\". " +
                "State your plan for finding meals.",
        maxTokens = 60
    )?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()

    /** Asks for a different search angle after a weak first pass. Null on any failure. */
    private suspend fun suggestAlternativeSearch(originalQuery: String, triedTerm: String, profile: UserProfile): String? {
        val json = AgentLlm.generateJson(
            prompt = "User asked: \"$originalQuery\" (goal: ${profile.goal}). " +
                    "Searching \"$triedTerm\" returned too few dishes that fit their macros well.\n" +
                    "Suggest ONE different search keyword/cuisine angle to try instead " +
                    "(e.g. switch from \"keto\" to \"high protein\", or from \"salad\" to \"grilled\").\n" +
                    "Return JSON: {\"searchTerm\": \"<different keyword>\"}",
            systemInstruction = "You are a search query interpreter for a food delivery app. Respond with JSON only."
        ) ?: return null
        return json.optString("searchTerm").takeIf { it.isNotBlank() }
    }

    /**
     * A single dish should cover one meal (~1/3 of the daily budget), never the whole
     * remaining day — but late in the day it shouldn't exceed what's actually left.
     */
    internal fun mealTarget(remaining: Int, daily: Int): Int =
        if (remaining > 0) minOf(remaining, daily / 3) else daily / 3

    /**
     * Checks if dish contains ingredients matching the user's allergies.
     */
    private fun containsAllergens(dish: DishItem, allergens: List<String>): Boolean {
        val text = (dish.name + " " + dish.description).lowercase(Locale.ROOT)
        for (allergen in allergens) {
            when (allergen) {
                "nut allergy", "nuts", "peanuts", "almonds" -> {
                    if (text.contains("almond") || text.contains("walnut") || text.contains("peanut") || text.contains("nut ")) {
                        return true
                    }
                }
                "gluten-free" -> {
                    // Filter out dishes with wheat, bread, wrap flour unless marked gluten-free
                    if (text.contains("roti") || text.contains("tortilla") || text.contains("wheat") || text.contains("flour")) {
                        if (!text.contains("gluten-free")) {
                            return true
                        }
                    }
                }
                "dairy-free" -> {
                    if (text.contains("paneer") || text.contains("butter") || text.contains("cheese") || text.contains("curd") || text.contains("milk") || text.contains("cream") || text.contains("ghee")) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isNonVeg(dish: DishItem): Boolean {
        val text = (dish.name + " " + dish.description).lowercase(Locale.ROOT)
        return text.contains("chicken") || text.contains("fish") || text.contains("egg") || text.contains("mutton") || text.contains("chicken")
    }

    private fun isVegan(dish: DishItem): Boolean {
        if (isNonVeg(dish)) return false
        val text = (dish.name + " " + dish.description).lowercase(Locale.ROOT)
        // Exclude dairy items
        return !(text.contains("paneer") || text.contains("cheese") || text.contains("ghee") || text.contains("cream") || text.contains("milk") || text.contains("curd") || text.contains("butttermilk"))
    }

    /**
     * Calculates the macro fit score using the specified ReAct formula:
     * score = (protein_match * 0.5) + (calorie_match * 0.3) + (price_match * 0.2)
     */
    private fun calculateMacroScore(
        dish: DishItem,
        targetCalories: Int,
        targetProtein: Int,
        targetCarbs: Int,
        targetFat: Int,
        targetPrice: Double
    ): Double {
        val proteinMatch = clampMatch(dish.proteinG.toDouble(), targetProtein.toDouble())
        val calorieMatch = clampMatch(dish.calories.toDouble(), targetCalories.toDouble())
        val priceMatch = clampMatch(dish.price, targetPrice)

        Log.d(TAG, "Dish: ${dish.name} -> P_Match: $proteinMatch, Cal_Match: $calorieMatch, Price_Match: $priceMatch")

        return (proteinMatch * 0.5) + (calorieMatch * 0.3) + (priceMatch * 0.2)
    }

    private fun clampMatch(estimated: Double, target: Double): Double {
        if (target <= 0.0) return 1.0
        val match = 1.0 - Math.abs(estimated - target) / target
        return Math.max(0.0, Math.min(1.0, match))
    }

    /**
     * One batched Gemini call: conversational response + one specific reasoning per dish.
     * Returns null on any failure so the template fallback below kicks in.
     */
    private suspend fun generateLlmResponse(
        query: String,
        profile: UserProfile,
        remainingCalories: Int,
        remainingProtein: Int,
        dishes: List<DishItem>,
        caveat: String? = null
    ): Pair<String, List<DishItem>>? {
        val dishLines = dishes.mapIndexed { i, d ->
            "$i. ${d.name} (${d.restaurantName}) — P:${d.proteinG}g C:${d.carbsG}g F:${d.fatG}g ${d.calories}kcal ₹${d.price}, fit ${(d.score * 100).toInt()}%"
        }.joinToString("\n")

        val json = AgentLlm.generateJson(
            prompt = "The user asked: \"$query\"\n" +
                    "Their goal: ${profile.goal}. Remaining today: ${remainingCalories}kcal, ${remainingProtein}g protein.\n" +
                    "Ranked dishes:\n$dishLines\n\n" +
                    (caveat?.let { "Important: $it, so these are only the closest macro matches — do not describe them as the requested food.\n" } ?: "") +
                    "Return JSON: {\"message\": \"<2-3 friendly sentences reacting to their request and summarizing why these picks fit>\", " +
                    "\"reasonings\": [\"<one specific sentence per dish, same order, why it suits this user right now>\"]}",
            systemInstruction = "You are MacroMate, a nutrition concierge for Swiggy food delivery in India. Respond with JSON only."
        ) ?: return null

        return try {
            val message = json.optString("message").takeIf { it.isNotBlank() } ?: return null
            val reasonings = json.optJSONArray("reasonings")
            val updated = dishes.mapIndexed { i, d ->
                val r = reasonings?.optString(i)
                if (r.isNullOrBlank()) d else d.copy(aiReasoning = r)
            }
            message to updated
        } catch (e: Exception) {
            Log.w(TAG, "Gemini response parse failed, using templates", e)
            null
        }
    }

    /**
     * Helper to retrieve user reasoning comments.
     */
    private fun generateAIReasoning(dish: DishItem, profile: UserProfile, score: Double): String {
        val percentage = (score * 100).toInt()
        val restStr = if (profile.allergiesJson.contains("vegetarian")) "pure vegetarian" else "curated nutrient"
        return when {
            dish.proteinG > 30 -> {
                "This high-protein powerhouse ($percentage% fit) packs a substantial ${dish.proteinG}g of clean muscle-building protein, keeping carbs perfectly at ${dish.carbsG}g. It aligns perfectly with your ${profile.goal} plan while satisfying your core dietary boundaries."
            }
            dish.carbsG < 15 -> {
                "Perfect ketogenic or low-carbohydrate choice ($percentage% fit). It has only ${dish.carbsG}g of carbs and relies on healthy lipids (${dish.fatG}g) and lean proteins (${dish.proteinG}g) to hit your targets."
            }
            else -> {
                "An extremely balanced, nutritional delivery ($percentage% fit). Delivers a moderate, clean glycemic index distribution (${dish.carbsG}g carbs) bundled with ${dish.proteinG}g of $restStr proteins."
            }
        }
    }

    /**
     * Safe JSON list parser helper.
     */
    private fun parseList(jsonStr: String): List<String> {
        return try {
            val list = mutableListOf<String>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
