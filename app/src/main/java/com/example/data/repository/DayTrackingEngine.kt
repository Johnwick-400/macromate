package com.example.data.repository

import com.example.data.model.DailyTargetSnapshot
import com.example.data.model.MacroLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DayTrackingStatus {
    COMPLETE,  // >= 800 kcal or >= 2 meals logged. Valid day.
    PARTIAL,   // Some logs, but < 800 kcal and < 2 meals.
    FASTED,    // Explicitly marked as fasting by user.
    ESTIMATED, // Rough user estimate ("ate as usual / light / heavy").
    UNTRACKED  // Nothing logged. Unknown data, NEVER 0.
}

data class DaySummary(
    val date: String,
    val status: DayTrackingStatus,
    val caloriesConsumed: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val targetCalories: Int,
    val targetProteinG: Int,
    val isWithinTargetTolerance: Boolean,
    val isValidForAverages: Boolean
)

data class WeeklyAdherenceReport(
    val totalCalendarDays: Int,
    val validDaysCount: Int,
    val loggingCoveragePercent: Int, // e.g. 4/7 = 57%
    val averageDailyCalories: Int?,  // null if < 4 valid days ("Not enough data")
    val adherencePercent: Int?,     // null if < 4 valid days
    val hasEnoughData: Boolean,      // true if validDaysCount >= 4 (MacroFactor threshold)
    val untrackedDates: List<String>
)

/**
 * Pure Kotlin engine for accurate day tracking and missed-day handling.
 * Enforces the core rule: Unknown data != 0 kcal / ₹0 spent.
 */
object DayTrackingEngine {

    /**
     * Determines tracking status for a specific day based on recorded logs.
     */
    fun classifyDay(
        date: String,
        logs: List<MacroLog>,
        snapshot: DailyTargetSnapshot? = null
    ): DaySummary {
        // If snapshot explicitly records FASTED or ESTIMATED
        if (snapshot?.status == "FASTED") {
            return DaySummary(
                date = date,
                status = DayTrackingStatus.FASTED,
                caloriesConsumed = 0,
                proteinG = 0,
                carbsG = 0,
                fatG = 0,
                targetCalories = snapshot.targetCalories,
                targetProteinG = snapshot.targetProteinG,
                isWithinTargetTolerance = true,
                isValidForAverages = true
            )
        }
        if (snapshot?.status == "ESTIMATED") {
            return DaySummary(
                date = date,
                status = DayTrackingStatus.ESTIMATED,
                caloriesConsumed = snapshot.estimatedCalories ?: snapshot.targetCalories,
                proteinG = snapshot.estimatedProtein ?: snapshot.targetProteinG,
                carbsG = 0,
                fatG = 0,
                targetCalories = snapshot.targetCalories,
                targetProteinG = snapshot.targetProteinG,
                isWithinTargetTolerance = true,
                isValidForAverages = true
            )
        }

        if (logs.isEmpty()) {
            val targetCal = snapshot?.targetCalories ?: 2000
            val targetP = snapshot?.targetProteinG ?: 140
            return DaySummary(
                date = date,
                status = DayTrackingStatus.UNTRACKED,
                caloriesConsumed = 0,
                proteinG = 0,
                carbsG = 0,
                fatG = 0,
                targetCalories = targetCal,
                targetProteinG = targetP,
                isWithinTargetTolerance = false,
                isValidForAverages = false // NEVER counted in averages!
            )
        }

        val totalCal = logs.sumOf { it.calories }
        val totalP = logs.sumOf { it.proteinG }
        val totalC = logs.sumOf { it.carbsG }
        val totalF = logs.sumOf { it.fatG }
        val mealsCount = logs.size

        val targetCal = snapshot?.targetCalories ?: 2000
        val targetP = snapshot?.targetProteinG ?: 140

        val isComplete = totalCal >= 800 || mealsCount >= 2
        val status = if (isComplete) DayTrackingStatus.COMPLETE else DayTrackingStatus.PARTIAL

        // On-target tolerance: within +-15% of target calories
        val isWithinTolerance = totalCal in (targetCal * 0.85).toInt()..(targetCal * 1.15).toInt()

        return DaySummary(
            date = date,
            status = status,
            caloriesConsumed = totalCal,
            proteinG = totalP,
            carbsG = totalC,
            fatG = totalF,
            targetCalories = targetCal,
            targetProteinG = targetP,
            isWithinTargetTolerance = isWithinTolerance,
            isValidForAverages = isComplete
        )
    }

    /**
     * Evaluates weekly logging and adherence across a 7-day window.
     */
    fun computeWeeklyReport(daySummaries: List<DaySummary>): WeeklyAdherenceReport {
        val totalDays = daySummaries.size
        val validDays = daySummaries.filter { it.isValidForAverages }
        val validCount = validDays.size

        val coverage = if (totalDays > 0) (validCount * 100) / totalDays else 0
        val untracked = daySummaries.filter { it.status == DayTrackingStatus.UNTRACKED }.map { it.date }

        // Threshold: at least 4 of 7 days must be valid to produce statistically meaningful metrics
        val hasEnoughData = validCount >= 4

        val avgCal = if (hasEnoughData) {
            validDays.sumOf { it.caloriesConsumed } / validCount
        } else null

        val adherence = if (hasEnoughData) {
            val onTargetCount = validDays.count { it.isWithinTargetTolerance }
            (onTargetCount * 100) / validCount
        } else null

        return WeeklyAdherenceReport(
            totalCalendarDays = totalDays,
            validDaysCount = validCount,
            loggingCoveragePercent = coverage,
            averageDailyCalories = avgCal,
            adherencePercent = adherence,
            hasEnoughData = hasEnoughData,
            untrackedDates = untracked
        )
    }
}
