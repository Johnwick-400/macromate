package com.example

import com.example.domain.schedule.LoggedMeal
import com.example.domain.schedule.MealScheduleEngine
import com.example.domain.schedule.MealSlotType
import com.example.domain.schedule.ScheduleRules
import com.example.domain.schedule.ScheduleInput
import com.example.domain.schedule.ScheduleTemplate
import com.example.domain.schedule.SlotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.math.floor

class MealScheduleEngineTest {

    private fun input(
        goal: String = "Maintain",
        weightKg: Double = 70.0,
        kcal: Int = 2400,
        protein: Int = 140,
        carbs: Int = 280,
        fat: Int = 70,
        meals: Int = 3,
        wake: LocalTime = LocalTime.of(7, 0),
        sleep: LocalTime = LocalTime.of(23, 0),
        workout: LocalTime? = null,
        template: ScheduleTemplate = ScheduleTemplate.BALANCED,
        preSleep: Boolean = false,
        vegetarian: Boolean = false
    ) = ScheduleInput(goal, weightKg, kcal, protein, carbs, fat, meals, wake, sleep, workout, template, preSleep, vegetarian)

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun slotCountsFollowMealsPerDay() {
        val expected = mapOf(
            2 to listOf(MealSlotType.BREAKFAST, MealSlotType.DINNER),
            3 to listOf(MealSlotType.BREAKFAST, MealSlotType.LUNCH, MealSlotType.DINNER),
            4 to listOf(MealSlotType.BREAKFAST, MealSlotType.LUNCH, MealSlotType.SNACK, MealSlotType.DINNER),
            5 to listOf(MealSlotType.BREAKFAST, MealSlotType.SNACK, MealSlotType.LUNCH, MealSlotType.SNACK, MealSlotType.DINNER),
            6 to listOf(
                MealSlotType.BREAKFAST, MealSlotType.SNACK, MealSlotType.LUNCH,
                MealSlotType.SNACK, MealSlotType.DINNER, MealSlotType.SNACK
            )
        )
        expected.forEach { (meals, types) ->
            val plan = MealScheduleEngine.buildDay(input(meals = meals), emptyList(), null)
            assertEquals("meals=$meals", types, plan.slots.map { it.type })
            val withPreSleep = MealScheduleEngine.buildDay(input(meals = meals, preSleep = true), emptyList(), null)
            assertEquals(meals + 1, withPreSleep.slots.size)
            assertEquals(MealSlotType.PRE_SLEEP, withPreSleep.slots.last().type)
        }
        // Clamped outside 2..6.
        assertEquals(2, MealScheduleEngine.buildDay(input(meals = 1), emptyList(), null).slots.size)
        assertEquals(6, MealScheduleEngine.buildDay(input(meals = 9), emptyList(), null).slots.size)
    }

    @Test
    fun firstMealAnHourAfterWakeAndLastMealBeforeBed() {
        val plan = MealScheduleEngine.buildDay(input(meals = 3), emptyList(), null)
        assertEquals(t(8), plan.slots.first().time)
        assertEquals(t(20, 30), plan.slots.last().time)
        val withPreSleep = MealScheduleEngine.buildDay(input(preSleep = true), emptyList(), null)
        assertEquals(t(22, 15), withPreSleep.slots.last().time)
    }

    @Test
    fun macroSumsEqualTargetsExactly() {
        val inputs = mutableListOf<ScheduleInput>()
        for (meals in 2..6) {
            for (template in ScheduleTemplate.values()) {
                inputs += input(meals = meals, template = template)
                inputs += input(meals = meals, template = template, preSleep = true, goal = "Bulk", kcal = 3127, protein = 173, carbs = 401, fat = 97)
                inputs += input(meals = meals, template = template, workout = t(18), kcal = 1873, protein = 151, carbs = 163, fat = 59, goal = "Cut")
                inputs += input(meals = meals, template = template, workout = t(6, 30), wake = t(5, 30), preSleep = true, kcal = 2201, protein = 131)
                inputs += input(meals = meals, template = template, workout = t(12), sleep = t(0, 30), kcal = 2555, carbs = 311, fat = 83)
            }
        }
        inputs.forEach { inp ->
            val slots = MealScheduleEngine.buildDay(inp, emptyList(), null).slots
            assertEquals("kcal $inp", inp.targetKcal, slots.sumOf { it.targetKcal })
            assertEquals("protein $inp", inp.targetProteinG, slots.sumOf { it.targetProteinG })
            assertEquals("carbs $inp", inp.targetCarbsG, slots.sumOf { it.targetCarbsG })
            assertEquals("fat $inp", inp.targetFatG, slots.sumOf { it.targetFatG })
            assertTrue(slots.all { it.targetKcal >= 0 && it.targetProteinG >= 0 && it.targetCarbsG >= 0 && it.targetFatG >= 0 })
        }
    }

    @Test
    fun frontLoadedGivesBiggerBreakfast() {
        val balanced = MealScheduleEngine.buildDay(input(meals = 3), emptyList(), null).slots
        val front = MealScheduleEngine.buildDay(input(meals = 3, template = ScheduleTemplate.FRONT_LOADED), emptyList(), null).slots
        assertTrue(front.first().targetKcal > balanced.first().targetKcal)
        // 2 meals, balanced: 45 / 55.
        val two = MealScheduleEngine.buildDay(input(meals = 2, kcal = 2000), emptyList(), null).slots
        assertEquals(900, two[0].targetKcal)
        assertEquals(1100, two[1].targetKcal)
    }

    @Test
    fun preSleepProteinIs30To40gFor70kgBulk() {
        val plan = MealScheduleEngine.buildDay(
            input(goal = "Bulk", weightKg = 70.0, meals = 4, preSleep = true, protein = 170), emptyList(), null
        )
        val preSleep = plan.slots.single { it.type == MealSlotType.PRE_SLEEP }
        assertTrue(preSleep.targetProteinG in 30..40)
        assertEquals(30, preSleep.targetProteinG) // 0.4 × 70 = 28 → clamped up to 30
        val heavy = MealScheduleEngine.buildDay(input(weightKg = 110.0, preSleep = true, protein = 200), emptyList(), null)
        assertEquals(40, heavy.slots.single { it.type == MealSlotType.PRE_SLEEP }.targetProteinG)
    }

    @Test
    fun postWorkoutMealTaggedWithin120MinOfWorkoutEnd() {
        // Workout 18:00–19:00; dinner at 20:30 is 90 min after end.
        val plan = MealScheduleEngine.buildDay(input(workout = t(18)), emptyList(), null)
        val post = plan.slots.single { "post-workout" in it.tags }
        assertEquals(MealSlotType.DINNER, post.type)
        val minutesAfterEnd = post.time.toSecondOfDay() / 60 - 19 * 60
        assertTrue(minutesAfterEnd in 0..120)
    }

    @Test
    fun postWorkoutSlotInsertedWhenNoMealFollowsSession() {
        // Wake 06:00, workout 07:30–08:30; meals at 07:00, 13:15, 19:30 → nothing within 2 h after.
        val plan = MealScheduleEngine.buildDay(input(wake = t(6), sleep = t(22), workout = t(7, 30)), emptyList(), null)
        val post = plan.slots.single { it.type == MealSlotType.POST_WORKOUT }
        assertEquals(t(9), post.time)
        assertTrue("post-workout" in post.tags)
        assertEquals(plan.slots.map { it.time }.sorted(), plan.slots.map { it.time })
        // Training-day shift: the inserted slot gets its base 8-weight share plus 5 % of daily kcal.
        assertTrue(post.targetKcal > 2400 * 0.05)
    }

    @Test
    fun preWorkoutMealIsCarbLeaning() {
        // Workout 18:00; lunch at 14:15 is 225 min before → pre-workout, carb-leaning.
        val inp = input(workout = t(18))
        val plan = MealScheduleEngine.buildDay(inp, emptyList(), null)
        val lunch = plan.slots.single { it.type == MealSlotType.LUNCH }
        assertTrue("pre-workout" in lunch.tags)
        assertTrue("carb-leaning" in lunch.tags)
        val carbShare = lunch.targetCarbsG.toDouble() / inp.targetCarbsG
        val kcalShare = lunch.targetKcal.toDouble() / inp.targetKcal
        assertTrue("carbs $carbShare vs kcal $kcalShare", carbShare > kcalShare * 1.15)

        val restDay = MealScheduleEngine.buildDay(input(), emptyList(), null)
        assertTrue(lunch.targetKcal > restDay.slots.single { it.type == MealSlotType.LUNCH }.targetKcal)
    }

    @Test
    fun preWorkoutSlotInsertedWhenGapAllows() {
        // 2 meals at 08:00 and 20:30; workout 17:00 → no meal 1–4 h before, gap from breakfast ≥ 2 h.
        val plan = MealScheduleEngine.buildDay(input(meals = 2, workout = t(17)), emptyList(), null)
        val pre = plan.slots.single { it.type == MealSlotType.PRE_WORKOUT }
        assertEquals(t(15, 30), pre.time)
        assertTrue("carb-leaning" in pre.tags)
        assertEquals("Banana with peanut chikki", pre.suggestion)
    }

    @Test
    fun warnsWhenMealProteinAboveUpperBand() {
        // 2 meals, 70 kg, 160 g → 80 g per meal = 1.14 g/kg > 0.55 g/kg (39 g).
        val plan = MealScheduleEngine.buildDay(input(meals = 2, protein = 160), emptyList(), null)
        assertEquals(80, plan.slots[0].targetProteinG)
        assertTrue(plan.warnings.any { it.startsWith("Breakfast protein 80 g is above 0.55 g/kg (39 g)") })
        assertTrue(plan.warnings.any { it.startsWith("Only 2 meals reach") })
        // Never capped.
        assertEquals(160, plan.slots.sumOf { it.targetProteinG })
    }

    @Test
    fun warnsWhenMealProteinBelowLowerBand() {
        val plan = MealScheduleEngine.buildDay(input(meals = 3, protein = 45), emptyList(), null)
        assertTrue(
            plan.warnings.contains(
                "Breakfast protein 15 g is below 0.25 g/kg (18 g). Adding a meal or a protein-rich snack helps spread protein."
            )
        )
    }

    @Test
    fun bulkNeedsFourProteinFeedings() {
        val bulk = MealScheduleEngine.buildDay(input(goal = "Bulk", meals = 3, protein = 140), emptyList(), null)
        assertTrue(bulk.warnings.any { it.contains("at least 4 protein feedings") })
        val maintain = MealScheduleEngine.buildDay(input(meals = 3, protein = 110), emptyList(), null)
        assertFalse(maintain.warnings.any { it.contains("protein feedings") })
    }

    @Test
    fun explicitSlotGoesToTheNearestSlotOfThatType() {
        // 5 meals: 08:00 B, 11:08 S, 14:15 L, 17:23 S, 20:30 D.
        val logs = listOf(
            LoggedMeal(t(10), MealSlotType.SNACK, 150, 5, 20, 5),
            LoggedMeal(t(10, 5), MealSlotType.SNACK, 120, 4, 15, 4), // same morning snack, not the 17:23 one
            LoggedMeal(t(21), MealSlotType.SNACK, 100, 3, 10, 3)
        )
        val slots = MealScheduleEngine.buildDay(input(meals = 5), logs, null).slots
        val snacks = slots.filter { it.type == MealSlotType.SNACK }
        assertEquals(270, snacks[0].loggedKcal)
        assertEquals(9, snacks[0].loggedProteinG)
        assertEquals(100, snacks[1].loggedKcal)
    }

    @Test
    fun unslottedLogGoesToNearestSlotByTime() {
        val logs = listOf(LoggedMeal(t(14), null, 700, 35, 80, 20))
        val slots = MealScheduleEngine.buildDay(input(meals = 5), logs, null).slots
        assertEquals(700, slots.single { it.type == MealSlotType.LUNCH }.loggedKcal)
        assertEquals(700, slots.sumOf { it.loggedKcal })
    }

    @Test
    fun explicitSlotOfMissingTypeBecomesAnExtraRow() {
        val logs = listOf(LoggedMeal(t(8, 10), MealSlotType.PRE_SLEEP, 300, 20, 30, 10))
        val slots = MealScheduleEngine.buildDay(input(meals = 3), logs, null).slots
        assertEquals(0, slots.single { it.type == MealSlotType.BREAKFAST }.loggedKcal) // not merged into breakfast
        val extra = slots.single { it.type == MealSlotType.PRE_SLEEP }
        assertEquals(300, extra.loggedKcal)
        assertTrue(ScheduleRules.EXTRA_TAG in extra.tags)
    }

    @Test
    fun statusesFollowClock() {
        val logs = listOf(LoggedMeal(t(8, 10), null, 600, 40, 60, 20))
        val at1400 = MealScheduleEngine.buildDay(input(), logs, t(14)).slots
        assertEquals(listOf(SlotStatus.LOGGED, SlotStatus.DUE_NOW, SlotStatus.UPCOMING), at1400.map { it.status })

        val at1600 = MealScheduleEngine.buildDay(input(), logs, t(16)).slots
        assertEquals(listOf(SlotStatus.LOGGED, SlotStatus.MISSED, SlotStatus.UPCOMING), at1600.map { it.status })

        // No clock (past/future day view): only LOGGED or UPCOMING.
        val viewOnly = MealScheduleEngine.buildDay(input(), logs, null).slots
        assertEquals(listOf(SlotStatus.LOGGED, SlotStatus.UPCOMING, SlotStatus.UPCOMING), viewOnly.map { it.status })
    }

    @Test
    fun rebalanceAfterMissedLunchRespectsCap() {
        val planned = MealScheduleEngine.buildDay(input(), emptyList(), null).slots
        val breakfast = planned[0]
        val logs = listOf(
            LoggedMeal(t(8, 5), MealSlotType.BREAKFAST, breakfast.targetKcal, breakfast.targetProteinG, breakfast.targetCarbsG, breakfast.targetFatG)
        )
        val slots = MealScheduleEngine.buildDay(input(), logs, t(16)).slots
        assertEquals(SlotStatus.MISSED, slots[1].status)
        // Logged and missed slots keep their planned numbers.
        assertEquals(planned[0].targetKcal, slots[0].targetKcal)
        assertEquals(planned[1].targetKcal, slots[1].targetKcal)
        // Dinner would need lunch + dinner, but is capped at 1.5× plan.
        val dinner = slots[2]
        assertEquals(floor(planned[2].targetKcal * 1.5).toInt(), dinner.targetKcal)
        assertEquals(floor(planned[2].targetProteinG * 1.5).toInt(), dinner.targetProteinG)
        assertTrue(breakfast.targetKcal + dinner.targetKcal <= 2400)
    }

    @Test
    fun rebalanceDistributesOwedExactlyWhenUnderCap() {
        val inp = input(meals = 5)
        val planned = MealScheduleEngine.buildDay(inp, emptyList(), null).slots
        // Ate 150 kcal / 10 g protein less than planned at breakfast.
        val b = planned[0]
        val logs = listOf(LoggedMeal(t(8), null, b.targetKcal - 150, b.targetProteinG - 10, b.targetCarbsG, b.targetFatG))
        val slots = MealScheduleEngine.buildDay(inp, logs, t(9, 30)).slots
        val open = slots.filter { it.status == SlotStatus.UPCOMING || it.status == SlotStatus.DUE_NOW }
        assertEquals(4, open.size)
        assertEquals(inp.targetKcal - logs[0].kcal, open.sumOf { it.targetKcal })
        assertEquals(inp.targetProteinG - logs[0].proteinG, open.sumOf { it.targetProteinG })
        open.forEachIndexed { i, s -> assertTrue(s.targetKcal <= planned[i + 1].targetKcal * 1.5) }
    }

    @Test
    fun overEatingNeverProducesNegativeTargets() {
        val logs = listOf(LoggedMeal(t(8), null, 5000, 300, 600, 200))
        val slots = MealScheduleEngine.buildDay(input(), logs, t(9)).slots
        assertTrue(slots.all { it.targetKcal >= 0 && it.targetProteinG >= 0 && it.targetCarbsG >= 0 && it.targetFatG >= 0 })
        assertEquals(0, slots.last().targetKcal)
    }

    @Test
    fun sleepAfterMidnightKeepsDinnerBeforePreSleep() {
        val plan = MealScheduleEngine.buildDay(input(meals = 4, sleep = t(0, 30), preSleep = true), emptyList(), null)
        val types = plan.slots.map { it.type }
        assertTrue(types.indexOf(MealSlotType.DINNER) < types.indexOf(MealSlotType.PRE_SLEEP))
        assertEquals(t(22), plan.slots.single { it.type == MealSlotType.DINNER }.time)
        assertEquals(t(23, 45), plan.slots.single { it.type == MealSlotType.PRE_SLEEP }.time)

        // Later still: pre-sleep wraps past midnight but stays last.
        val late = MealScheduleEngine.buildDay(input(meals = 4, sleep = t(1, 30), preSleep = true), emptyList(), null)
        assertEquals(MealSlotType.PRE_SLEEP, late.slots.last().type)
        assertEquals(t(0, 45), late.slots.last().time)

        // A snack logged at 00:15 on that day maps to pre-sleep, not breakfast.
        val logged = MealScheduleEngine.buildDay(
            input(meals = 4, sleep = t(1, 30), preSleep = true),
            listOf(LoggedMeal(t(0, 15), null, 200, 25, 10, 5)), t(0, 20)
        )
        assertEquals(SlotStatus.LOGGED, logged.slots.last().status)
    }

    @Test
    fun suggestionsAreVegAwareAndCutAddsVegetables() {
        val veg = MealScheduleEngine.buildDay(input(vegetarian = true), emptyList(), null).slots
        assertEquals("Poha with peanuts + curd", veg[0].suggestion)
        val cut = MealScheduleEngine.buildDay(input(goal = "Cut"), emptyList(), null).slots
        assertEquals("Egg bhurji with 2 rotis", cut[0].suggestion)
        assertEquals("Rice, dal, chicken curry, salad (add extra vegetables)", cut[1].suggestion)
    }

    @Test
    fun tipsAreSoft() {
        val cut = MealScheduleEngine.buildDay(input(goal = "Cut"), emptyList(), null)
        assertTrue(cut.tips.any { it.contains("Front-loaded") })
        assertTrue(cut.tips.any { it.contains("water") })
        assertFalse(cut.tips.any { it.startsWith("Finishing dinner") })

        // Short day forces dinner close to bed.
        val shortDay = MealScheduleEngine.buildDay(input(meals = 6, wake = t(12), sleep = t(18)), emptyList(), null)
        assertTrue(shortDay.tips.any { it.contains("under 2.5 h") })
    }
}
