package com.example

import com.example.data.model.DayStatus
import com.example.domain.DayClock
import com.example.domain.analytics.ActivityCounter
import com.example.domain.analytics.AdherenceCalculator
import com.example.domain.analytics.ClassifiedDay
import com.example.domain.analytics.DayClassifier
import com.example.domain.analytics.DayInput
import com.example.domain.analytics.DayKind
import com.example.domain.analytics.DayTarget
import com.example.domain.analytics.EstimateLevel
import com.example.domain.analytics.SafetyRules
import com.example.domain.analytics.WeightPoint
import com.example.domain.analytics.WeightTrend
import com.example.domain.budget.BudgetEngine
import com.example.domain.budget.BudgetPace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BudgetAndAnalyticsTest {

    private val target = DayTarget(kcal = 2000, proteinG = 110, carbsG = 250, fatG = 60)
    private val sep27 = LocalDate.of(2026, 9, 27)

    // ---------- Budget ----------

    @Test
    fun `safe daily allowance divides what is left by days left including today`() {
        val s = BudgetEngine.status(6000.0, null, spent = 3800.0, today = sep27)!!
        assertEquals(4, s.daysLeft)              // 27, 28, 29, 30
        assertEquals(550.0, s.dailyAllowance!!, 0.001)
        assertEquals(2200.0, s.remaining, 0.001)
    }

    @Test
    fun `no budget means the feature is off`() {
        assertNull(BudgetEngine.status(0.0, null, spent = 500.0, today = sep27))
    }

    @Test
    fun `overspending shows used up with no fake allowance`() {
        val s = BudgetEngine.status(6000.0, null, spent = 6400.0, today = sep27)!!
        assertEquals(BudgetPace.USED_UP, s.pace)
        assertNull(s.dailyAllowance)
        assertEquals(0.0, s.remaining, 0.001)
    }

    @Test
    fun `new month starts fresh because spend is derived per month`() {
        val oct1 = LocalDate.of(2026, 10, 1)
        val s = BudgetEngine.status(6000.0, null, spent = 0.0, today = oct1)!!
        assertEquals(31, s.daysLeft)
        assertEquals(BudgetPace.ON_TRACK, s.pace)
    }

    @Test
    fun `spending ahead of an even pace is flagged`() {
        // By Sep 16 (15 full days) an even pace is 3000; 3800 is >10% ahead.
        val s = BudgetEngine.status(6000.0, null, spent = 3800.0, today = LocalDate.of(2026, 9, 16))!!
        assertEquals(BudgetPace.OVER_PACE, s.pace)
    }

    @Test
    fun `budget entered mid month is the whole month's budget, as typed`() {
        // ₹6,000 entered on Sep 27 with ₹1,200 already spent this month.
        val s = BudgetEngine.status(6000.0, LocalDate.of(2026, 9, 27), spent = 1200.0, today = sep27)!!
        assertEquals(6000.0, s.budgetForPeriod, 0.001)
        assertEquals(4800.0, s.remaining, 0.001)
        assertEquals(1200.0, s.dailyAllowance!!, 0.001) // 4800 / 4 days left
        assertTrue(s.unconfirmedSpendDays.isEmpty())     // nothing to ask before the budget existed
    }

    @Test
    fun `days with no spend info are reported instead of assumed zero`() {
        val known = setOf(LocalDate.of(2026, 9, 25))
        val s = BudgetEngine.status(6000.0, LocalDate.of(2026, 9, 24), 300.0, sep27, known)!!
        assertEquals(listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 26)), s.unconfirmedSpendDays)
    }

    @Test
    fun `reconcile adds only the difference`() {
        assertEquals(700.0, BudgetEngine.reconcileAdjustment(3800.0, 4500.0), 0.001)
    }

    // ---------- Day classification ----------

    private fun day(
        date: LocalDate, kcal: Int = 0, times: List<LocalTime> = emptyList(),
        status: DayStatus = DayStatus.AUTO, estimate: Int? = null, isToday: Boolean = false,
        dayTarget: DayTarget = target
    ) = DayClassifier.classify(
        DayInput(date, dayTarget, status, estimate, kcal, kcal / 20, times, isToday)
    )

    @Test
    fun `empty past day is untracked and unknown, never zero`() {
        val d = day(sep27.minusDays(1))
        assertEquals(DayKind.UNTRACKED, d.kind)
        assertNull(d.intakeKcal)
        assertTrue(d.needsAnswer)
        assertFalse(d.countsInAverages)
    }

    @Test
    fun `800 kcal or two eating occasions make a complete day`() {
        assertEquals(DayKind.COMPLETE, day(sep27.minusDays(1), 900, listOf(LocalTime.NOON)).kind)
        assertEquals(DayKind.COMPLETE, day(sep27.minusDays(1), 600, listOf(LocalTime.of(9, 0), LocalTime.of(20, 0))).kind)
        // Two logs 20 minutes apart are one eating occasion -> still partial.
        assertEquals(DayKind.PARTIAL, day(sep27.minusDays(1), 500, listOf(LocalTime.of(13, 0), LocalTime.of(13, 20))).kind)
    }

    @Test
    fun `a zero day only counts when the user marks it fasted`() {
        val fasted = day(sep27.minusDays(1), status = DayStatus.FASTED)
        assertEquals(DayKind.FASTED, fasted.kind)
        assertEquals(0, fasted.intakeKcal)
        assertTrue(fasted.countsInAverages)
    }

    @Test
    fun `quick estimate is relative to that day's own target`() {
        assertEquals(1600, DayClassifier.quickEstimateKcal(target, EstimateLevel.LIGHTER))
        assertEquals(2600, DayClassifier.quickEstimateKcal(target, EstimateLevel.HEAVIER))
        val est = day(sep27.minusDays(1), status = DayStatus.ESTIMATED, estimate = 2600)
        assertEquals(DayKind.ESTIMATED, est.kind)
        assertNull(est.proteinG) // estimates carry kcal only
        assertFalse(est.countsForCarryOver)
    }

    @Test
    fun `today is in progress and never judged`() {
        assertEquals(DayKind.IN_PROGRESS, day(sep27, 300, listOf(LocalTime.of(9, 0)), isToday = true).kind)
    }

    // ---------- The "missed 3 days" case ----------

    private fun missedThreeDaysWeek(): List<ClassifiedDay> {
        val mon = LocalDate.of(2026, 9, 21)
        val on = listOf(LocalTime.of(9, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
        return listOf(
            day(mon, 2000, on), day(mon.plusDays(1), 2100, on),
            day(mon.plusDays(2)), day(mon.plusDays(3)), day(mon.plusDays(4)), // missed Wed-Fri
            day(mon.plusDays(5), 1950, on), day(mon.plusDays(6), 2500, on)
        )
    }

    @Test
    fun `missed days are excluded from averages instead of dragging them to zero`() {
        val s = AdherenceCalculator.summarize(missedThreeDaysWeek())
        assertEquals(7, s.elapsedDays)
        assertEquals(4, s.validDays)
        assertEquals(4.0 / 7, s.coverage, 1e-9)
        assertEquals(2138, s.avgKcal) // 8550 / 4 valid days, not 8550 / 7 = 1221
        assertEquals(3, s.onTargetDays)                           // 2500 is >10% over
        assertEquals(0.75, s.adherence!!, 1e-9)
        assertTrue(s.enoughData)
        assertEquals(3, s.daysNeedingAnswer.size)
    }

    @Test
    fun `fewer than four valid days in a week reads not enough data`() {
        val week = missedThreeDaysWeek().toMutableList()
        week[6] = day(LocalDate.of(2026, 9, 27)) // Sunday also missed -> 3 valid
        assertFalse(AdherenceCalculator.summarize(week).enoughData)
    }

    @Test
    fun `each day is judged against the target it had that day`() {
        val cut = DayTarget(1600, 130, 150, 50)
        val d = day(sep27.minusDays(2), 1650, listOf(LocalTime.NOON), dayTarget = cut)
        val later = day(sep27.minusDays(1), 2050, listOf(LocalTime.NOON), dayTarget = target)
        val s = AdherenceCalculator.summarize(listOf(d, later))
        assertEquals(2, s.onTargetDays) // 1650 vs 1600 and 2050 vs 2000 both within 10%
    }

    // ---------- Weekly carry-over ----------

    @Test
    fun `untracked days never create extra calories`() {
        val days = missedThreeDaysWeek().take(5) // Mon, Tue logged; Wed-Fri missed
        // Known days: -0 and -100 kcal banked => small negative spread, not +3 days of "savings".
        val carry = AdherenceCalculator.carryOverKcal(days, todayTargetKcal = 2000, daysLeftInWeekInclToday = 2)
        assertEquals(-50, carry)
    }

    @Test
    fun `carry over is capped at 10 percent of today's target`() {
        val fasted = listOf(day(LocalDate.of(2026, 9, 21), status = DayStatus.FASTED))
        assertEquals(200, AdherenceCalculator.carryOverKcal(fasted, 2000, 6))
    }

    @Test
    fun `carry over never pushes today below the floor`() {
        val binge = listOf(day(LocalDate.of(2026, 9, 21), 4000, listOf(LocalTime.NOON), dayTarget = DayTarget(1300, 100, 120, 40)))
        assertEquals(-100, AdherenceCalculator.carryOverKcal(binge, 1300, 3))
    }

    // ---------- Weight trend ----------

    @Test
    fun `missing weigh-ins are interpolated before smoothing`() {
        val pts = WeightTrend.compute(
            listOf(WeightPoint(LocalDate.of(2026, 9, 21), 151.0), WeightPoint(LocalDate.of(2026, 9, 23), 150.0))
        )
        assertEquals(3, pts.size)
        assertTrue(pts[1].interpolated)
        assertNull(pts[1].measuredKg)
        // Tuesday's filled value is 150.5; the trend moves 10% toward it from 151.
        assertEquals(151.0 + 0.1 * (150.5 - 151.0), pts[1].trendKg, 1e-9)
    }

    // ---------- Day boundary ----------

    @Test
    fun `a 1 AM snack belongs to the previous day`() {
        val oneAm = LocalDateTime.of(2026, 9, 27, 1, 0)
        assertEquals("2026-09-26", DayClock.logicalDateString(oneAm, dayEndsAtHour = 4))
        assertEquals("2026-09-27", DayClock.logicalDateString(oneAm.withHour(5), dayEndsAtHour = 4))
    }

    // ---------- Safety gating ----------

    @Test
    fun `healthy adult has no safety reasons`() {
        assertTrue(SafetyRules.reasons(28, 70.0, 175.0, false, false, false).isEmpty())
    }

    @Test
    fun `each health flag, minors and underweight pause carry-over`() {
        assertEquals(listOf("pregnancy or breastfeeding"), SafetyRules.reasons(28, 60.0, 165.0, true, false, false))
        assertEquals(listOf("diabetes medication"), SafetyRules.reasons(45, 80.0, 170.0, false, true, false))
        assertEquals(listOf("eating-disorder history"), SafetyRules.reasons(22, 55.0, 160.0, false, false, true))
        assertEquals(listOf("age under 18"), SafetyRules.reasons(16, 55.0, 165.0, false, false, false))
        assertEquals(listOf("BMI under 18.5"), SafetyRules.reasons(24, 48.0, 170.0, false, false, false)) // BMI 16.6
    }

    // ---------- Activity counts (from the very first meal / glass) ----------

    @Test
    fun `one meal and one glass today already count`() {
        val a = ActivityCounter.count(
            mealDates = listOf(sep27), waterByDate = mapOf(sep27 to 1),
            from = sep27.minusDays(6), to = sep27, today = sep27, todayKcal = 450
        )
        assertEquals(1, a.mealsLogged)
        assertEquals(1, a.daysWithMeals)
        assertEquals(1, a.waterGlasses)
        assertEquals(1.0, a.avgWaterPerDay!!, 0.0)
        assertEquals(450, a.todayKcal)
    }

    @Test
    fun `activity only counts inside the period and days with water`() {
        val a = ActivityCounter.count(
            mealDates = listOf(sep27.minusDays(10), sep27.minusDays(1), sep27.minusDays(1), sep27),
            waterByDate = mapOf(sep27.minusDays(1) to 6, sep27 to 0, sep27.minusDays(20) to 8),
            from = sep27.minusDays(6), to = sep27, today = sep27, todayKcal = 0
        )
        assertEquals(3, a.mealsLogged)
        assertEquals(2, a.daysWithMeals)
        assertEquals(6, a.waterGlasses)
        assertEquals(1, a.daysWithWater) // a day with 0 glasses isn't a water day
    }
}
