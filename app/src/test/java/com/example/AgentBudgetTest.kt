package com.example

import com.example.data.repository.MacroMateAgent
import com.example.data.repository.MealBudget
import com.example.data.repository.PriceGuide
import com.example.data.repository.PriceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBudgetTest {

    private val allowance = MealBudget(dailyAllowanceInr = 220.0, usedUp = false)

    @Test
    fun `no budget keeps the original restaurant-average pricing`() {
        val guide = MacroMateAgent.resolvePriceGuide("high protein dinner", MealBudget.NONE)
        assertEquals(PriceGuide.NONE, guide)
        assertEquals(350.0, guide.targetPriceFor(350.0), 0.0)
        assertNull(guide.overBudgetNote(500.0))
    }

    @Test
    fun `daily allowance caps the price target but never raises it`() {
        val guide = MacroMateAgent.resolvePriceGuide("dinner", allowance)
        assertEquals(PriceSource.DAILY_BUDGET, guide.source)
        assertEquals(220.0, guide.targetPriceFor(350.0), 0.0)
        assertEquals(180.0, guide.targetPriceFor(180.0), 0.0)
    }

    @Test
    fun `a price typed by the user wins over the budget`() {
        val guide = MacroMateAgent.resolvePriceGuide("lunch under ₹300", allowance)
        assertEquals(PriceSource.USER_QUERY, guide.source)
        assertEquals(300.0, guide.targetPriceFor(450.0), 0.0)
        assertNull(guide.overBudgetNote(320.0)) // the user chose this price
    }

    @Test
    fun `cheat meal requests ignore the budget`() {
        assertEquals(PriceGuide.NONE, MacroMateAgent.resolvePriceGuide("cheat meal pizza", allowance))
    }

    @Test
    fun `over-budget dishes get a soft note instead of being hidden`() {
        val guide = MacroMateAgent.resolvePriceGuide("dinner", allowance)
        assertEquals(" ₹40 over today's ₹220 budget.", guide.overBudgetNote(260.0))
        assertNull(guide.overBudgetNote(200.0))
    }

    @Test
    fun `used-up budget favours lower-priced dishes`() {
        val guide = MacroMateAgent.resolvePriceGuide("dinner", MealBudget(null, usedUp = true))
        assertEquals(PriceSource.BUDGET_USED_UP, guide.source)
        assertTrue(guide.targetPriceFor(300.0) < 300.0)
    }
}
