package com.example.domain.budget

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class BudgetPace { ON_TRACK, OVER_PACE, USED_UP }

data class BudgetStatus(
    val budgetForPeriod: Double,   // the monthly budget exactly as the user entered it
    val spent: Double,             // known spend in the period (app orders + manual costs + outside/fees/reconcile)
    val remaining: Double,
    val daysLeft: Int,             // including today
    val dailyAllowance: Double?,   // null once the budget is used up — never a fake floor
    val fractionUsed: Double,      // 0..1+ (can exceed 1 when overspent)
    val pace: BudgetPace,
    val periodStart: LocalDate,    // first day we ask about outside spend (budget set date, or the 1st)
    val unconfirmedSpendDays: List<LocalDate> // past days with no spend info at all (spend may be missing)
)

/**
 * Monthly food budget maths. Pure and deterministic.
 *
 * - Spend is always derived from stored rows, so there is no counter to drift and the
 *   month rolls over on its own (a new month simply has no rows yet).
 * - No budget (<= 0) means the feature is off: callers keep today's price behaviour.
 * - Safe-to-spend = remaining / days left, recomputed daily (Depo, PocketGuard, YNAB practice).
 */
object BudgetEngine {

    /** Spend more than 10% ahead of an even pace counts as over pace. */
    private const val OVER_PACE_TOLERANCE = 1.10

    fun status(
        monthlyBudget: Double,
        budgetStartDate: LocalDate?,
        spent: Double,
        today: LocalDate,
        daysWithSpendInfo: Set<LocalDate> = emptySet()
    ): BudgetStatus? {
        if (monthlyBudget <= 0.0) return null

        val monthStart = today.withDayOfMonth(1)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        // The amount entered is the budget for the whole calendar month, and spend is counted
        // for the whole month too. The start date only limits which days we ask about.
        val budget = monthlyBudget
        val periodStart = budgetStartDate
            ?.takeIf { !it.isBefore(monthStart) && !it.isAfter(today) }
            ?: monthStart

        val daysLeft = ChronoUnit.DAYS.between(today, monthEnd).toInt() + 1
        val elapsedInMonth = today.dayOfMonth - 1 // full days before today
        val remaining = budget - spent
        val expectedByNow = budget * elapsedInMonth / today.lengthOfMonth()

        val pace = when {
            remaining <= 0.0 -> BudgetPace.USED_UP
            elapsedInMonth > 0 && spent > expectedByNow * OVER_PACE_TOLERANCE -> BudgetPace.OVER_PACE
            else -> BudgetPace.ON_TRACK
        }

        val askFrom = ChronoUnit.DAYS.between(periodStart, today).toInt()
        val unconfirmed = (0 until askFrom)
            .map { periodStart.plusDays(it.toLong()) }
            .filterNot { it in daysWithSpendInfo }

        return BudgetStatus(
            budgetForPeriod = budget,
            spent = spent,
            remaining = remaining.coerceAtLeast(0.0),
            daysLeft = daysLeft,
            dailyAllowance = if (remaining > 0.0) remaining / daysLeft else null,
            fractionUsed = spent / budget,
            pace = pace,
            periodStart = periodStart,
            unconfirmedSpendDays = unconfirmed
        )
    }

    /** Amount to record so known spend matches what the user says they actually spent. */
    fun reconcileAdjustment(knownSpend: Double, actualSpend: Double): Double = actualSpend - knownSpend
}
