package com.example.domain.schedule

import java.time.LocalTime

enum class MealSlotType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    SNACK("Snack"),
    PRE_WORKOUT("Pre-workout"),
    POST_WORKOUT("Post-workout snack"),
    DINNER("Dinner"),
    PRE_SLEEP("Pre-sleep")
}

enum class ScheduleTemplate { BALANCED, FRONT_LOADED }

enum class SlotStatus { LOGGED, DUE_NOW, UPCOMING, MISSED }

/** Inputs the engine needs; built from UserProfile + the day's target snapshot. */
data class ScheduleInput(
    val goal: String,                    // "Cut" | "Maintain" | "Bulk"
    val weightKg: Double,
    val targetKcal: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int,
    val mealsPerDay: Int,                // 2..6 main eating occasions (excludes optional pre-sleep)
    val wakeTime: LocalTime,
    val sleepTime: LocalTime,
    val workoutTime: LocalTime?,         // null = rest day / no training anchor
    val template: ScheduleTemplate,
    val preSleepProtein: Boolean,        // default on for Bulk
    val vegetarian: Boolean
)

/** A meal already eaten today, reduced to what the engine needs. */
data class LoggedMeal(
    val time: LocalTime,
    val slot: MealSlotType?,             // explicit assignment wins over time matching
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val id: Int = 0                      // MacroLog id, so a section knows which items it holds
)

data class MealSlot(
    val type: MealSlotType,
    val time: LocalTime,
    val targetKcal: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int,
    val tags: Set<String> = emptySet(),  // "pre-workout", "post-workout", "carb-leaning"
    val loggedKcal: Int = 0,
    val loggedProteinG: Int = 0,
    val status: SlotStatus = SlotStatus.UPCOMING,
    val loggedIds: List<Int> = emptyList(), // MacroLog ids shown in this section
    val suggestion: String = ""          // plain-language food idea (Indian context)
)

data class DayPlan(
    val slots: List<MealSlot>,
    val warnings: List<String>,          // e.g. protein per meal outside 0.25–0.55 g/kg
    val tips: List<String>               // soft, non-prescriptive guidance
)
