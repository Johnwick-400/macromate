package com.example.data.repository

import kotlin.math.roundToInt

/** What the Monthly Food Budget tells the agent for this query. NONE = no budget set. */
data class MealBudget(val dailyAllowanceInr: Double?, val usedUp: Boolean) {
    companion object {
        val NONE = MealBudget(dailyAllowanceInr = null, usedUp = false)
    }
}

enum class PriceSource { NONE, USER_QUERY, DAILY_BUDGET, BUDGET_USED_UP }

/** Resolved price target used by the deterministic scoring pass. */
data class PriceGuide(val ceilingInr: Double?, val source: PriceSource) {

    fun targetPriceFor(restaurantAveragePrice: Double): Double = when (source) {
        PriceSource.NONE -> restaurantAveragePrice
        PriceSource.USER_QUERY -> ceilingInr ?: restaurantAveragePrice
        // Cheaper of today's allowance and the restaurant's usual price.
        PriceSource.DAILY_BUDGET -> minOf(ceilingInr ?: restaurantAveragePrice, restaurantAveragePrice)
        // Budget exhausted: favour the lower-priced end of each menu.
        PriceSource.BUDGET_USED_UP -> restaurantAveragePrice * 0.6
    }

    /** Soft tag appended to a dish's reasoning — the dish is never hidden. */
    fun overBudgetNote(dishPrice: Double): String? {
        val cap = ceilingInr ?: return null
        if (source != PriceSource.DAILY_BUDGET || dishPrice <= cap) return null
        return " ₹${(dishPrice - cap).roundToInt()} over today's ₹${cap.roundToInt()} budget."
    }

    val thinkingNote: String?
        get() = when (source) {
            PriceSource.NONE -> null
            PriceSource.USER_QUERY -> "Price target from your request: ₹${ceilingInr?.roundToInt()}"
            PriceSource.DAILY_BUDGET -> "Monthly budget: ₹${ceilingInr?.roundToInt()} safe to spend today — favouring dishes within it"
            PriceSource.BUDGET_USED_UP -> "Monthly budget used up — favouring lower-priced dishes"
        }

    companion object {
        val NONE = PriceGuide(null, PriceSource.NONE)
    }
}
