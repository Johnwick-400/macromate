package com.example

import com.example.data.model.OrderHistory
import com.example.data.model.OutsideSpendLog
import com.example.data.repository.FoodBudgetCalculator
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FoodBudgetTest {

    @Test
    fun budgetDisabledWhenZero() {
        val status = FoodBudgetCalculator.evaluateBudget(
            monthlyBudgetInr = 0.0,
            currentMonthSpendInr = 1500.0
        )
        assertNull(status.safeDailyAllowanceInr)
        assertFalse(status.isBudgetExhausted)
        assertEquals("Monthly budget not set", status.statusMessage)
    }

    @Test
    fun computesAccurateSafeDailyAllowance() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 21) // 10 days remaining in a 30-day month
            set(Calendar.MONTH, Calendar.SEPTEMBER) // Sep has 30 days
        }
        val status = FoodBudgetCalculator.evaluateBudget(
            monthlyBudgetInr = 6000.0,
            currentMonthSpendInr = 4000.0, // 2000 remaining
            calendar = cal
        )
        // 2000 remaining / 10 days = 200/day
        assertNotNull(status.safeDailyAllowanceInr)
        assertEquals(200.0, status.safeDailyAllowanceInr!!, 1.0)
        assertFalse(status.isBudgetExhausted)
    }

    @Test
    fun handlesExhaustedBudgetCleanly() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val status = FoodBudgetCalculator.evaluateBudget(
            monthlyBudgetInr = 5000.0,
            currentMonthSpendInr = 5200.0,
            calendar = cal
        )
        assertTrue(status.isBudgetExhausted)
        assertEquals(0.0, status.safeDailyAllowanceInr!!, 0.01)
        assertTrue(status.statusMessage.contains("Budget used up"))
    }

    @Test
    fun monthlySpendAggregatesOrdersAndOutsideSpend() {
        val sepTime = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse("2026-09-15")?.time ?: 0L
        val augTime = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse("2026-08-15")?.time ?: 0L

        val orders = listOf(
            OrderHistory(orderId = "o1", userId = "u1", itemsJson = "", totalPrice = 450.0, createdAt = sepTime),
            OrderHistory(orderId = "o2", userId = "u1", itemsJson = "", totalPrice = 320.0, createdAt = sepTime),
            OrderHistory(orderId = "o3", userId = "u1", itemsJson = "", totalPrice = 999.0, createdAt = augTime) // Past month
        )
        val outside = listOf(
            OutsideSpendLog(id = 1, userId = "u1", date = "2026-09-14", amountInr = 150.0, note = "Juice stall"),
            OutsideSpendLog(id = 2, userId = "u1", date = "2026-08-10", amountInr = 200.0, note = "Old")
        )
        val total = FoodBudgetCalculator.computeMonthlySpend(orders, outside, yearMonthKey = "2026-09")
        assertEquals(450.0 + 320.0 + 150.0, total, 0.01)
    }
}
