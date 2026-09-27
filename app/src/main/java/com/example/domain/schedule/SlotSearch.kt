package com.example.domain.schedule

import java.time.LocalDate

/**
 * Turns a meal slot into a concrete food to search for. A goal-only request ("breakfast around
 * 600 kcal") names no food, so every slot used to hit the same few restaurants; naming a food
 * that suits the slot (and the user's diet) gives each meal its own results. The choice rotates
 * by date so the same slot suggests different food on different days.
 */
object SlotSearch {
    private val VEG = mapOf(
        MealSlotType.BREAKFAST to listOf("idli", "dosa", "poha", "upma", "uttapam"),
        MealSlotType.LUNCH to listOf("rice bowl", "khichdi", "thali", "rajma"),
        MealSlotType.SNACK to listOf("salad", "buttermilk", "sprouts", "chana"),
        MealSlotType.PRE_WORKOUT to listOf("oats", "fruit", "banana"),
        MealSlotType.POST_WORKOUT to listOf("paneer", "tofu", "soya"),
        MealSlotType.DINNER to listOf("paneer", "roti", "dal", "soya"),
        MealSlotType.PRE_SLEEP to listOf("paneer", "curd", "milk")
    )
    private val NON_VEG = mapOf(
        MealSlotType.BREAKFAST to listOf("egg", "idli", "dosa", "omelette"),
        MealSlotType.LUNCH to listOf("chicken rice bowl", "rice bowl", "biryani", "khichdi"),
        MealSlotType.SNACK to listOf("salad", "buttermilk", "egg", "sprouts"),
        MealSlotType.PRE_WORKOUT to listOf("oats", "fruit", "banana"),
        MealSlotType.POST_WORKOUT to listOf("chicken", "egg", "paneer"),
        MealSlotType.DINNER to listOf("chicken", "paneer", "fish", "roti"),
        MealSlotType.PRE_SLEEP to listOf("paneer", "curd", "milk")
    )

    fun foodFor(type: MealSlotType, vegetarian: Boolean, date: LocalDate): String {
        val options = (if (vegetarian) VEG else NON_VEG).getValue(type)
        return options[date.dayOfYear % options.size]
    }

    /** e.g. "idli for breakfast around 600 kcal with 22g protein under ₹550". */
    fun query(slot: MealSlot, vegetarian: Boolean, date: LocalDate, dailyAllowanceInr: Int?): String {
        val food = foodFor(slot.type, vegetarian, date)
        val budget = dailyAllowanceInr?.let { " under ₹$it" }.orEmpty()
        return "$food for ${slot.type.label.lowercase()} around ${slot.targetKcal} kcal with ${slot.targetProteinG}g protein$budget"
    }
}
