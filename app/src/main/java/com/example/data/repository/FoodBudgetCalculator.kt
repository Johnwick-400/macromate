package com.example.data.repository

import com.example.data.model.OrderHistory
import com.example.data.model.OutsideSpendLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BudgetStatus(
    val monthlyBudgetInr: Double,
    val currentMonthSpendInr: Double,
    val remainingBudgetInr: Double,
    val daysRemainingInMonth: Int,
    val safeDailyAllowanceInr: Double?, // null if monthly budget is 0 (disabled)
    val isBudgetExhausted: Boolean,
    val statusMessage: String
)

/**
 * Pure Kotlin budget & daily spending calculator.
 * Independent of Android UI / DB for 100% unit-testability.
 */
object FoodBudgetCalculator {

    fun currentYearMonth(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(date)

    /**
     * Computes the total spend for a given month key (e.g. "2026-09")
     * from confirmed Swiggy orders and recorded outside food spends.
     */
    fun computeMonthlySpend(
        orders: List<OrderHistory>,
        outsideSpends: List<OutsideSpendLog> = emptyList(),
        yearMonthKey: String = currentYearMonth()
    ): Double {
        val formatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val orderTotal = orders.filter { order ->
            formatter.format(Date(order.createdAt)) == yearMonthKey
        }.sumOf { it.totalPrice }

        val outsideTotal = outsideSpends.filter { spend ->
            spend.date.startsWith(yearMonthKey)
        }.sumOf { it.amountInr }

        return orderTotal + outsideTotal
    }

    /**
     * Calculates the safe daily allowance:
     * remainingBudget / daysRemaining in the current calendar month.
     */
    fun evaluateBudget(
        monthlyBudgetInr: Double,
        currentMonthSpendInr: Double,
        calendar: Calendar = Calendar.getInstance()
    ): BudgetStatus {
        if (monthlyBudgetInr <= 0.0) {
            return BudgetStatus(
                monthlyBudgetInr = 0.0,
                currentMonthSpendInr = currentMonthSpendInr,
                remainingBudgetInr = 0.0,
                daysRemainingInMonth = 0,
                safeDailyAllowanceInr = null,
                isBudgetExhausted = false,
                statusMessage = "Monthly budget not set"
            )
        }

        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val maxDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = (maxDaysInMonth - currentDay + 1).coerceAtLeast(1)

        val remaining = (monthlyBudgetInr - currentMonthSpendInr).coerceAtLeast(0.0)
        val isExhausted = currentMonthSpendInr >= monthlyBudgetInr

        val dailyAllowance = if (isExhausted) {
            0.0
        } else {
            (remaining / daysRemaining)
        }

        val message = when {
            isExhausted -> "Budget used up for this month"
            dailyAllowance < 150.0 -> "Tight budget: ₹${dailyAllowance.toInt()}/day remaining"
            else -> "On track: ₹${dailyAllowance.toInt()}/day remaining ($daysRemaining days left)"
        }

        return BudgetStatus(
            monthlyBudgetInr = monthlyBudgetInr,
            currentMonthSpendInr = currentMonthSpendInr,
            remainingBudgetInr = remaining,
            daysRemainingInMonth = daysRemaining,
            safeDailyAllowanceInr = dailyAllowance,
            isBudgetExhausted = isExhausted,
            statusMessage = message
        )
    }
}
