package com.example.data.repository

import com.example.data.model.DishItem
import java.util.Locale

/**
 * Hard constraints parsed from a natural-language food request
 * (e.g. "strict vegetarian meal under ₹200 around 450 calories").
 *
 * These are *hard filters* applied to results AFTER the deterministic macro scoring in
 * [MacroMateAgent] — the agent's tested scoring math is never touched. Parsing is pure,
 * on-device regex (no network, no latency), so it's safe to run on both the typed-chat and
 * voice paths. When nothing is detected, every field is null and [MealConstraints.filter]
 * is a no-op — the existing flow behaves exactly as before.
 */
data class MealConstraints(
    val maxPrice: Double? = null,
    val targetCalories: Int? = null,
    val vegOnly: Boolean = false
) {
    val hasAny: Boolean get() = maxPrice != null || targetCalories != null || vegOnly

    companion object {
        // ±20% band around a requested calorie figure counts as "around N calories".
        private const val CALORIE_TOLERANCE = 0.20

        fun parse(query: String): MealConstraints {
            val q = query.lowercase(Locale.ROOT)

            // Price: "under ₹200", "under 200 rs", "below 200", "< 200", "200 rupees"
            val maxPrice = Regex("""(?:under|below|less than|max|upto|up to|<)\s*₹?\s*(\d{2,5})""")
                .find(q)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: Regex("""₹\s*(\d{2,5})""").find(q)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: Regex("""(\d{2,5})\s*(?:rs|rupees|inr)\b""").find(q)?.groupValues?.get(1)?.toDoubleOrNull()

            // Calories: "450 cal", "450 calories", "450 kcal", "around 450 cal"
            val targetCalories = Regex("""(\d{2,4})\s*(?:k?cal|calories|calorie)\b""")
                .find(q)?.groupValues?.get(1)?.toIntOrNull()

            // Strict vegetarian / vegan intent
            val vegOnly = Regex("""\b(veg|vegetarian|vegan)\b""").containsMatchIn(q)

            return MealConstraints(maxPrice, targetCalories, vegOnly)
        }
    }

    /** Applies the hard filters to a scored dish list. No-op when [hasAny] is false. */
    fun filter(dishes: List<DishItem>): List<DishItem> {
        if (!hasAny) return dishes
        return dishes.filter { d ->
            (maxPrice == null || d.price <= maxPrice) &&
            (targetCalories == null || withinCalorieBand(d.calories)) &&
            (!vegOnly || isVegetarianDish(d))
        }
    }

    private fun withinCalorieBand(calories: Int): Boolean {
        val t = targetCalories ?: return true
        val tol = (t * CALORIE_TOLERANCE)
        return calories <= t + tol   // at or under target (+tolerance); lower is always fine
    }

    private fun isVegetarianDish(dish: DishItem): Boolean {
        val text = (dish.name + " " + dish.description).lowercase(Locale.ROOT)
        val nonVeg = listOf(
            "chicken", "mutton", "fish", "prawn", "egg", "meat", "lamb",
            "beef", "pork", "keema", "tikka", "kebab", "seafood"
        )
        return nonVeg.none { text.contains(it) }
    }

    /** Human-readable summary for status/voice lines, e.g. "veg · under ₹200 · ~450 kcal". */
    fun describe(): String = buildList {
        if (vegOnly) add("veg")
        maxPrice?.let { add("under ₹${it.toInt()}") }
        targetCalories?.let { add("~$it kcal") }
    }.joinToString(" · ")
}
