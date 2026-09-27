package com.example.domain.analytics

import com.example.data.model.DayStatus
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a day is treated in analytics. UNTRACKED and PARTIAL are unknown — never zero. */
enum class DayKind { COMPLETE, PARTIAL, FASTED, ESTIMATED, UNTRACKED, IN_PROGRESS }

data class DayTarget(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

data class DayInput(
    val date: LocalDate,
    val target: DayTarget,
    val status: DayStatus,          // what the user told us (AUTO if nothing)
    val estimatedKcal: Int?,
    val loggedKcal: Int,            // own meals only (shared items carry 0 macros)
    val loggedProteinG: Int,
    val mealTimes: List<LocalTime>, // one entry per logged meal, for eating-occasion counting
    val isToday: Boolean
)

data class ClassifiedDay(
    val date: LocalDate,
    val kind: DayKind,
    val target: DayTarget,
    val intakeKcal: Int?,           // null = unknown
    val proteinG: Int?,             // null = unknown (estimates carry kcal only)
    val implausible: Boolean,
    val needsAnswer: Boolean        // past PARTIAL/UNTRACKED day the user hasn't resolved
) {
    val countsInAverages get() = kind == DayKind.COMPLETE || kind == DayKind.FASTED || kind == DayKind.ESTIMATED
    val countsForCarryOver get() = kind == DayKind.COMPLETE || kind == DayKind.FASTED
}

/**
 * Classifies days the way Cronometer/MacroFactor and diet-tracking research do:
 * a valid day has >= 800 kcal logged or >= 2 eating occasions (Patel et al.; Turner-McGrievy 2019),
 * a fast only counts when the user says so, and an empty day is unknown rather than 0 kcal.
 */
object DayClassifier {
    const val COMPLETE_MIN_KCAL = 800
    const val COMPLETE_MIN_OCCASIONS = 2
    /** Meals closer together than this are one eating occasion. */
    private const val OCCASION_GAP_MIN = 60

    // Willett plausibility range, sex-agnostic (500–3,500 women, 800–4,000 men).
    private const val PLAUSIBLE_MIN = 500
    private const val PLAUSIBLE_MAX = 4000

    fun eatingOccasions(times: List<LocalTime>): Int {
        if (times.isEmpty()) return 0
        val sorted = times.sorted()
        var count = 1
        var last = sorted.first()
        for (t in sorted.drop(1)) {
            if (java.time.Duration.between(last, t).toMinutes() >= OCCASION_GAP_MIN) count++
            last = t
        }
        return count
    }

    fun classify(day: DayInput): ClassifiedDay {
        val hasLogs = day.mealTimes.isNotEmpty() || day.loggedKcal > 0
        val kind = when (day.status) {
            DayStatus.FASTED -> DayKind.FASTED
            DayStatus.ESTIMATED -> if (day.estimatedKcal != null) DayKind.ESTIMATED else DayKind.UNTRACKED
            DayStatus.COMPLETE -> DayKind.COMPLETE
            DayStatus.SKIPPED -> DayKind.UNTRACKED
            DayStatus.AUTO -> when {
                day.isToday -> DayKind.IN_PROGRESS
                day.loggedKcal >= COMPLETE_MIN_KCAL ||
                    eatingOccasions(day.mealTimes) >= COMPLETE_MIN_OCCASIONS -> DayKind.COMPLETE
                hasLogs -> DayKind.PARTIAL
                else -> DayKind.UNTRACKED
            }
        }
        val intake = when (kind) {
            DayKind.COMPLETE, DayKind.FASTED, DayKind.IN_PROGRESS -> day.loggedKcal
            DayKind.ESTIMATED -> day.estimatedKcal
            DayKind.PARTIAL, DayKind.UNTRACKED -> null
        }
        val protein = if (kind == DayKind.COMPLETE || kind == DayKind.FASTED || kind == DayKind.IN_PROGRESS)
            day.loggedProteinG else null
        return ClassifiedDay(
            date = day.date,
            kind = kind,
            target = day.target,
            intakeKcal = intake,
            proteinG = protein,
            implausible = kind == DayKind.COMPLETE && intake != null && (intake < PLAUSIBLE_MIN || intake > PLAUSIBLE_MAX),
            needsAnswer = !day.isToday && day.status == DayStatus.AUTO &&
                (kind == DayKind.PARTIAL || kind == DayKind.UNTRACKED)
        )
    }

    /** "Lighter / as planned / heavier" quick estimate, relative to that day's own target. */
    fun quickEstimateKcal(target: DayTarget, level: EstimateLevel): Int =
        (target.kcal * level.factor).roundToInt()
}

/**
 * What the user actually did in a period, counted from the very first meal or glass of water
 * (today included) — shown alongside the day-quality analytics, which need full days.
 */
data class ActivitySummary(
    val mealsLogged: Int,
    val daysWithMeals: Int,
    val waterGlasses: Int,
    val daysWithWater: Int,
    val todayKcal: Int
) {
    val avgWaterPerDay: Double? get() = if (daysWithWater == 0) null else waterGlasses.toDouble() / daysWithWater
}

object ActivityCounter {
    fun count(
        mealDates: List<LocalDate>,          // one entry per own logged meal
        waterByDate: Map<LocalDate, Int>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        todayKcal: Int
    ): ActivitySummary {
        val inRange = { d: LocalDate -> !d.isBefore(from) && !d.isAfter(to) }
        val meals = mealDates.filter(inRange)
        val water = waterByDate.filter { (d, g) -> inRange(d) && g > 0 }
        return ActivitySummary(
            mealsLogged = meals.size,
            daysWithMeals = meals.toSet().size,
            waterGlasses = water.values.sum(),
            daysWithWater = water.size,
            todayKcal = if (inRange(today)) todayKcal else 0
        )
    }
}

enum class EstimateLevel(val factor: Double) { LIGHTER(0.8), AS_PLANNED(1.0), HEAVIER(1.3) }

data class PeriodSummary(
    val elapsedDays: Int,          // past days in the range (today excluded while in progress)
    val validDays: Int,            // complete + fasted + estimated
    val coverage: Double,          // validDays / elapsedDays
    val adherence: Double?,        // on-target days / (complete + estimated); null if none
    val onTargetDays: Int,
    val fastedDays: Int,
    val avgKcal: Int?,             // over valid days only
    val avgProteinG: Int?,         // over days with known protein only
    val enoughData: Boolean,       // false -> show "Not enough data" instead of numbers
    val daysNeedingAnswer: List<LocalDate>
)

object AdherenceCalculator {
    /** Within ±10% of the day's own target counts as on target. */
    const val ON_TARGET_TOLERANCE = 0.10
    /** MacroFactor holds its estimates below 4 logged days a week. */
    const val MIN_VALID_DAYS_PER_WEEK = 4

    fun summarize(days: List<ClassifiedDay>): PeriodSummary {
        val past = days.filter { it.kind != DayKind.IN_PROGRESS }
        val valid = past.filter { it.countsInAverages }
        val judged = valid.filter { it.kind == DayKind.COMPLETE || it.kind == DayKind.ESTIMATED }
        val onTarget = judged.count { d ->
            val intake = d.intakeKcal ?: return@count false
            d.target.kcal > 0 && abs(intake - d.target.kcal).toDouble() / d.target.kcal <= ON_TARGET_TOLERANCE
        }
        val withProtein = valid.mapNotNull { it.proteinG }
        val enough = when {
            past.size >= 7 -> valid.size >= MIN_VALID_DAYS_PER_WEEK * past.size / 7
            else -> valid.size >= minOf(MIN_VALID_DAYS_PER_WEEK, past.size) && valid.isNotEmpty()
        }
        return PeriodSummary(
            elapsedDays = past.size,
            validDays = valid.size,
            coverage = if (past.isEmpty()) 0.0 else valid.size.toDouble() / past.size,
            adherence = if (judged.isEmpty()) null else onTarget.toDouble() / judged.size,
            onTargetDays = onTarget,
            fastedDays = valid.count { it.kind == DayKind.FASTED },
            avgKcal = valid.mapNotNull { it.intakeKcal }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            avgProteinG = withProtein.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            enoughData = enough,
            daysNeedingAnswer = past.filter { it.needsAnswer }.map { it.date }
        )
    }

    /**
     * Weekly carry-over for today. Only days we actually know (complete or fasted) contribute;
     * the adjustment is spread over the days left in the week, capped at ±10% of today's target
     * and never below the floor. Missed days never create "punishment" calories.
     */
    fun carryOverKcal(
        weekDaysBeforeToday: List<ClassifiedDay>,
        todayTargetKcal: Int,
        daysLeftInWeekInclToday: Int,
        floorKcal: Int = 1200
    ): Int {
        val banked = weekDaysBeforeToday
            .filter { it.countsForCarryOver }
            .sumOf { (it.target.kcal - (it.intakeKcal ?: it.target.kcal)) }
        if (banked == 0 || daysLeftInWeekInclToday <= 0) return 0
        val cap = (todayTargetKcal * ON_TARGET_TOLERANCE).roundToInt()
        val perDay = (banked / daysLeftInWeekInclToday).coerceIn(-cap, cap)
        val minAdjust = floorKcal - todayTargetKcal // most negative allowed so today stays >= floor
        return if (perDay < 0) maxOf(perDay, minOf(0, minAdjust)) else perDay
    }
}
