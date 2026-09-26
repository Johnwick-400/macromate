package com.example.data.repository

import com.example.data.model.MealGoalType
import java.util.Locale

data class ScheduledMealSlot(
    val slotIndex: Int,
    val name: String, // e.g. "Breakfast", "Lunch", "Post-Workout", "Dinner", "Bedtime Casein"
    val timeFormatted: String, // e.g. "08:00 AM"
    val timeMinutes: Int,
    val targetCalories: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int,
    val isPostWorkout: Boolean = false,
    val isBedtimeSlowProtein: Boolean = false,
    val suggestedFoodTypes: String
)

data class DailySchedulePlan(
    val totalCalories: Int,
    val totalProteinG: Int,
    val totalCarbsG: Int,
    val totalFatG: Int,
    val slots: List<ScheduledMealSlot>,
    val goalGuideline: String
)

/**
 * Pure Kotlin evidence-based meal schedule and macro distribution engine.
 * Synthesizes ISSN, ACSM, WHO, and ICMR-NIN nutritional timing guidelines.
 */
object MealScheduleCalculator {

    /**
     * Formats minutes from midnight into 12-hour AM/PM string (e.g. 480 -> "08:00 AM").
     */
    fun formatMinutesToAmPm(minutes: Int): String {
        val normalized = ((minutes % 1440) + 1440) % 1440
        val hour24 = normalized / 60
        val minute = normalized % 60
        val hour12 = when (val h = hour24 % 12) {
            0 -> 12
            else -> h
        }
        val amPm = if (hour24 < 12) "AM" else "PM"
        return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, amPm)
    }

    /**
     * Calculates optimal meal timing and macro distribution.
     *
     * @param weightKg User bodyweight in kilograms (used for ~0.4g/kg protein thresholds)
     * @param dailyCalories Total target daily calories
     * @param dailyProteinG Total target daily protein in grams
     * @param dailyCarbsG Total target daily carbs in grams
     * @param dailyFatG Total target daily fat in grams
     * @param mealsPerDay Total meals/snacks per day (default 4, range 3..6)
     * @param wakeMinutes Wake time in minutes from midnight (default 420 = 7:00 AM)
     * @param sleepMinutes Bedtime in minutes from midnight (default 1380 = 11:00 PM)
     * @param workoutMinutes Workout time in minutes from midnight (null or -1 if no workout)
     * @param isEarlyHeavyCut Optional front-loaded calories for Cut phase to blunt evening hunger
     */
    fun buildSchedule(
        weightKg: Double,
        dailyCalories: Int,
        dailyProteinG: Int,
        dailyCarbsG: Int,
        dailyFatG: Int,
        mealsPerDay: Int = 4,
        wakeMinutes: Int = 420,
        sleepMinutes: Int = 1380,
        workoutMinutes: Int? = 1080, // 6:00 PM
        isEarlyHeavyCut: Boolean = false
    ): DailySchedulePlan {
        val count = mealsPerDay.coerceIn(3, 6)
        val activeWindow = if (sleepMinutes > wakeMinutes) {
            sleepMinutes - wakeMinutes
        } else {
            (1440 - wakeMinutes) + sleepMinutes
        }

        // Calculate slot times: spaced evenly across active waking hours
        val interval = activeWindow / count
        val slotTimes = (0 until count).map { i ->
            (wakeMinutes + (i * interval) + (interval / 4)) % 1440
        }

        // Protein threshold: ISSN recommends ~0.4 g/kg per meal (e.g. ~28g for 70kg person)
        val minProteinPerMeal = (weightKg * 0.4).toInt().coerceAtLeast(20)

        // Calorie distribution ratios across meals
        val calorieRatios = when (count) {
            3 -> if (isEarlyHeavyCut) listOf(0.45, 0.35, 0.20) else listOf(0.33, 0.34, 0.33)
            4 -> if (isEarlyHeavyCut) listOf(0.35, 0.30, 0.20, 0.15) else listOf(0.28, 0.32, 0.25, 0.15)
            5 -> if (isEarlyHeavyCut) listOf(0.30, 0.25, 0.20, 0.15, 0.10) else listOf(0.22, 0.28, 0.20, 0.18, 0.12)
            else -> List(count) { 1.0 / count }
        }

        val slots = mutableListOf<ScheduledMealSlot>()

        for (i in 0 until count) {
            val tMinutes = slotTimes[i]
            val isLast = i == count - 1
            val isPostWorkout = workoutMinutes != null && workoutMinutes >= 0 &&
                    (tMinutes in workoutMinutes..(workoutMinutes + 120) ||
                            (tMinutes < workoutMinutes && (tMinutes + 1440) in workoutMinutes..(workoutMinutes + 120)))

            val name = when {
                i == 0 -> "Breakfast"
                isLast -> "Bedtime / Dinner"
                isPostWorkout -> "Post-Workout Meal"
                i == 1 && count >= 4 -> "Lunch"
                i == count - 2 -> "Evening Snack"
                else -> "Meal ${i + 1}"
            }

            val slotCal = (dailyCalories * calorieRatios[i]).toInt()

            // Protein distribution: ensure bedtime has 30-40g slow protein if possible,
            // post-workout has elevated protein, and others meet minProteinPerMeal
            val slotProtein = when {
                isLast -> 35.coerceAtMost(dailyProteinG / count + 10).coerceAtLeast(minProteinPerMeal)
                isPostWorkout -> (dailyProteinG / count + 8).coerceAtLeast(minProteinPerMeal)
                else -> (dailyProteinG / count).coerceAtLeast(minProteinPerMeal)
            }

            val slotCarbs = (dailyCarbsG * calorieRatios[i]).toInt()
            val slotFat = (dailyFatG * calorieRatios[i]).toInt()

            val foodSuggestions = when {
                isLast -> "Slow-digesting protein: Low-fat paneer, Greek curd/yogurt, warm spiced milk"
                isPostWorkout -> "Fast-absorbing recovery: Grilled chicken/egg white bowl, whey/sprouts with rice or roti"
                i == 0 -> "High-fiber breakfast: Moong dal chilla, oats khichdi, eggs with multigrain toast"
                else -> "Balanced Indian plate: Dal/chicken curry, seasonal sabzi, brown rice or whole-wheat roti"
            }

            slots.add(
                ScheduledMealSlot(
                    slotIndex = i,
                    name = name,
                    timeFormatted = formatMinutesToAmPm(tMinutes),
                    timeMinutes = tMinutes,
                    targetCalories = slotCal,
                    targetProteinG = slotProtein,
                    targetCarbsG = slotCarbs,
                    targetFatG = slotFat,
                    isPostWorkout = isPostWorkout,
                    isBedtimeSlowProtein = isLast,
                    suggestedFoodTypes = foodSuggestions
                )
            )
        }

        val guideline = when {
            isEarlyHeavyCut -> "Cut Phase: Front-loaded calories to suppress ghrelin and evening cravings."
            count >= 4 -> "Optimal 4+ meal protein distribution for muscle protein synthesis (~0.4g/kg/meal)."
            else -> "Standard 3-meal cadence fitting daily target macros."
        }

        return DailySchedulePlan(
            totalCalories = dailyCalories,
            totalProteinG = dailyProteinG,
            totalCarbsG = dailyCarbsG,
            totalFatG = dailyFatG,
            slots = slots,
            goalGuideline = guideline
        )
    }
}
