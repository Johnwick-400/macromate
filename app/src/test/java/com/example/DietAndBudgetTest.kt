package com.example

import com.example.data.model.BudgetTier
import com.example.data.model.DishItem
import com.example.data.repository.LayaDecisionEngine
import com.example.data.repository.MacroMateAgent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DietAndBudgetTest {
    private fun dish(name: String, desc: String = "") = DishItem(id = name, name = name, description = desc, price = 200.0)

    @Test
    fun meatIsDetectedByWholeWordSoVegetariansAreProtected() {
        assertTrue(MacroMateAgent.isNonVeg(dish("Mix'N'Match Value Duos- Non Veg")))
        assertTrue(MacroMateAgent.isNonVeg(dish("All American Lamb Smash Burger")))
        assertTrue(MacroMateAgent.isNonVeg(dish("Egg & Cheese Breakfast Burger")))
        assertTrue(MacroMateAgent.isNonVeg(dish("Loaded Fries", "topped with crispy bacon bits")))
        assertFalse(MacroMateAgent.isNonVeg(dish("Veggie Bean & Oats Burger")))
        assertFalse(MacroMateAgent.isNonVeg(dish("Paneer Tikka Burger (Whole Wheat)")))
        assertFalse(MacroMateAgent.isNonVeg(dish("Eggplant Parmigiana")))
    }

    @Test
    fun budgetOnlyAppliesWhenThePriceIsMentioned() = runBlocking {
        assertEquals(BudgetTier.PREMIUM_UNRESTRICTED, LayaDecisionEngine.evaluateIntent("burgers", 100, 500).budget)
        assertEquals(BudgetTier.BUDGET_UNDER_200, LayaDecisionEngine.evaluateIntent("cheap burger under 150", 100, 500).budget)
        assertEquals(BudgetTier.STANDARD_UNDER_400, LayaDecisionEngine.evaluateIntent("lunch under 300 rupees", 100, 500).budget)
    }
}
