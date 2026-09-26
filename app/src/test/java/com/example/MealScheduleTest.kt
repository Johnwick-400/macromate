package com.example

import com.example.data.repository.MealScheduleCalculator
import org.junit.Assert.*
import org.junit.Test

class MealScheduleTest {

    @Test
    fun formatsMinutesToAmPmAccurately() {
        assertEquals("12:00 AM", MealScheduleCalculator.formatMinutesToAmPm(0))
        assertEquals("07:30 AM", MealScheduleCalculator.formatMinutesToAmPm(450))
        assertEquals("12:00 PM", MealScheduleCalculator.formatMinutesToAmPm(720))
        assertEquals("01:15 PM", MealScheduleCalculator.formatMinutesToAmPm(795))
        assertEquals("11:45 PM", MealScheduleCalculator.formatMinutesToAmPm(1425))
    }

    @Test
    fun buildsFourMealScheduleWithAdequateProteinPerMeal() {
        val plan = MealScheduleCalculator.buildSchedule(
            weightKg = 75.0,
            dailyCalories = 2200,
            dailyProteinG = 160,
            dailyCarbsG = 220,
            dailyFatG = 65,
            mealsPerDay = 4,
            wakeMinutes = 420,  // 07:00 AM
            sleepMinutes = 1380, // 11:00 PM
            workoutMinutes = 1080 // 06:00 PM
        )

        assertEquals(4, plan.slots.size)
        // Check protein meets ISSN threshold (~0.4g/kg = 30g minimum per meal)
        plan.slots.forEach { slot ->
            assertTrue("Slot ${slot.name} has ${slot.targetProteinG}g protein", slot.targetProteinG >= 30)
        }

        // Bedtime slot has slow-digesting protein flag
        val bedtimeSlot = plan.slots.last()
        assertTrue(bedtimeSlot.isBedtimeSlowProtein)
        assertTrue(bedtimeSlot.suggestedFoodTypes.contains("Slow-digesting"))
    }

    @Test
    fun handlesEarlyHeavyCutDistribution() {
        val plan = MealScheduleCalculator.buildSchedule(
            weightKg = 70.0,
            dailyCalories = 1800,
            dailyProteinG = 140,
            dailyCarbsG = 180,
            dailyFatG = 50,
            mealsPerDay = 4,
            isEarlyHeavyCut = true
        )
        // First slot (Breakfast) should have more calories than the last slot
        val breakfast = plan.slots.first()
        val dinner = plan.slots.last()
        assertTrue(breakfast.targetCalories > dinner.targetCalories)
        assertTrue(plan.goalGuideline.contains("Cut Phase"))
    }
}
