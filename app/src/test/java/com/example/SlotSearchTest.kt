package com.example

import com.example.data.repository.extractFoodTerm
import com.example.domain.schedule.MealSlot
import com.example.domain.schedule.MealSlotType
import com.example.domain.schedule.SlotSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SlotSearchTest {
    private val day = LocalDate.of(2026, 9, 27)

    private fun slot(type: MealSlotType) = MealSlot(type, LocalTime.NOON, 600, 25, 70, 20)

    @Test
    fun `each slot searches for its own food instead of the same generic query`() {
        val foods = listOf(MealSlotType.BREAKFAST, MealSlotType.LUNCH, MealSlotType.SNACK, MealSlotType.DINNER)
            .map { SlotSearch.foodFor(it, vegetarian = true, date = day) }
        assertEquals(foods.size, foods.toSet().size)
    }

    @Test
    fun `vegetarian users never get meat or egg searches`() {
        val meat = listOf("chicken", "egg", "fish", "omelette", "biryani")
        for (type in MealSlotType.values()) for (d in 0L..10L) {
            val food = SlotSearch.foodFor(type, vegetarian = true, date = day.plusDays(d))
            assertFalse("$type -> $food", meat.any { it in food })
        }
    }

    @Test
    fun `the food in the query is what the agent searches for`() {
        val q = SlotSearch.query(slot(MealSlotType.BREAKFAST), vegetarian = true, date = day, dailyAllowanceInr = 550)
        assertTrue(q.endsWith("under ₹550"))
        val food = extractFoodTerm(q)
        assertNotNull(food)
        assertEquals(SlotSearch.foodFor(MealSlotType.BREAKFAST, true, day), food)
    }

    @Test
    fun `suggestions rotate across days`() {
        val week = (0L..6L).map { SlotSearch.foodFor(MealSlotType.BREAKFAST, true, day.plusDays(it)) }.toSet()
        assertTrue(week.size > 1)
    }
}
