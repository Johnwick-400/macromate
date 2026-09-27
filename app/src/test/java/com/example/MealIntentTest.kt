package com.example

import com.example.domain.schedule.LoggedMeal
import com.example.domain.schedule.MealIntent
import com.example.domain.schedule.MealScheduleEngine
import com.example.domain.schedule.MealSlot
import com.example.domain.schedule.MealSlotType
import com.example.domain.schedule.ScheduleInput
import com.example.domain.schedule.ScheduleRules
import com.example.domain.schedule.ScheduleTemplate
import com.example.domain.schedule.SlotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MealIntentTest {

    // ---------- Words -> section (typed or voice transcripts) ----------

    @Test
    fun `meal words map to the right section`() {
        val cases = mapOf(
            "order me high protein breakfast" to MealSlotType.BREAKFAST,
            "something for tiffin under 150" to MealSlotType.BREAKFAST,
            "Get me lunch please" to MealSlotType.LUNCH,
            "healthy snacks under 200" to MealSlotType.SNACK,
            "evening snack with protein" to MealSlotType.SNACK,
            "mid-morning snack" to MealSlotType.SNACK,
            "paneer for dinner" to MealSlotType.DINNER,
            "what can I eat tonight" to MealSlotType.DINNER,
            "post workout meal with chicken" to MealSlotType.POST_WORKOUT,
            "something light before the gym" to MealSlotType.PRE_WORKOUT,
            "a snack before bed" to MealSlotType.PRE_SLEEP
        )
        cases.forEach { (text, expected) -> assertEquals(text, expected, MealIntent.detect(text)) }
    }

    @Test
    fun `no meal named returns null so the schedule decides`() {
        assertNull(MealIntent.detect("burgers"))
        assertNull(MealIntent.detect("high protein under 300"))
    }

    // ---------- No meal named -> the user's next meal ----------

    private fun slot(type: MealSlotType, status: SlotStatus, tags: Set<String> = emptySet()) =
        MealSlot(type, LocalTime.NOON, 500, 20, 60, 15, tags = tags, status = status)

    @Test
    fun `next meal is the one due now, else the next upcoming`() {
        val plan = listOf(
            slot(MealSlotType.BREAKFAST, SlotStatus.LOGGED),
            slot(MealSlotType.LUNCH, SlotStatus.DUE_NOW),
            slot(MealSlotType.DINNER, SlotStatus.UPCOMING)
        )
        assertEquals(MealSlotType.LUNCH, MealIntent.nextSlot(plan))
        assertEquals(MealSlotType.DINNER, MealIntent.nextSlot(plan.map { if (it.type == MealSlotType.LUNCH) it.copy(status = SlotStatus.MISSED) else it }))
    }

    @Test
    fun `a late order counts toward dinner, not the pre-sleep snack`() {
        val plan = listOf(
            slot(MealSlotType.DINNER, SlotStatus.MISSED),
            slot(MealSlotType.PRE_SLEEP, SlotStatus.MISSED)
        )
        assertEquals(MealSlotType.DINNER, MealIntent.nextSlot(plan))
    }

    // ---------- Engine: explicit sections are respected ----------

    private fun input(meals: Int) = ScheduleInput(
        goal = "Maintain", weightKg = 70.0, targetKcal = 2000, targetProteinG = 110, targetCarbsG = 250, targetFatG = 60,
        mealsPerDay = meals, wakeTime = LocalTime.of(7, 0), sleepTime = LocalTime.of(23, 0), workoutTime = null,
        template = ScheduleTemplate.BALANCED, preSleepProtein = false, vegetarian = true
    )

    @Test
    fun `a dinner ordered at 5 PM lands in dinner, not the nearby snack`() {
        val plan = MealScheduleEngine.buildDay(
            input(4),
            listOf(LoggedMeal(LocalTime.of(17, 0), MealSlotType.DINNER, 650, 30, 70, 20, id = 7)),
            now = LocalTime.of(17, 5)
        )
        val dinner = plan.slots.single { it.type == MealSlotType.DINNER }
        assertEquals(650, dinner.loggedKcal)
        assertEquals(listOf(7), dinner.loggedIds)
        assertEquals(0, plan.slots.single { it.type == MealSlotType.SNACK }.loggedKcal)
    }

    @Test
    fun `a snack in a 3-meal plan gets its own extra row and totals stay exact`() {
        val plan = MealScheduleEngine.buildDay(
            input(3),
            listOf(LoggedMeal(LocalTime.of(16, 30), MealSlotType.SNACK, 250, 8, 30, 10, id = 3)),
            now = null
        )
        val extra = plan.slots.single { it.type == MealSlotType.SNACK }
        assertTrue(ScheduleRules.EXTRA_TAG in extra.tags)
        assertEquals(250, extra.loggedKcal)
        assertEquals(listOf(3), extra.loggedIds)
        assertEquals(0, extra.targetKcal)
        assertEquals(2000, plan.slots.sumOf { it.targetKcal })           // targets untouched
        assertEquals(0, plan.slots.filter { it.type != MealSlotType.SNACK }.sumOf { it.loggedKcal })
    }

    @Test
    fun `older untagged logs still go to the nearest meal`() {
        val plan = MealScheduleEngine.buildDay(input(3), listOf(LoggedMeal(LocalTime.of(8, 10), null, 400, 15, 50, 12, id = 1)), now = null)
        assertEquals(400, plan.slots.single { it.type == MealSlotType.BREAKFAST }.loggedKcal)
    }
}
