package com.example

import com.example.data.model.DishItem
import com.example.data.model.UserProfile
import com.example.data.repository.AgentState
import com.example.data.repository.MacroMateAgent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric for android.util.Log inside the agent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MacroMateAgentScoringTest {

    @Test
    fun `mealTarget is one third of daily, capped by remaining budget`() {
        assertEquals(43, MacroMateAgent.mealTarget(remaining = 130, daily = 130)) // fresh day → daily/3
        assertEquals(30, MacroMateAgent.mealTarget(remaining = 30, daily = 130))  // late day → what's left
        assertEquals(43, MacroMateAgent.mealTarget(remaining = 0, daily = 130))   // budget blown → daily/3
        assertEquals(43, MacroMateAgent.mealTarget(remaining = -20, daily = 130))
    }

    private fun dishWithScore(score: Double) = DishItem(id = "d", name = "d", description = "", price = 0.0, score = score)

    @Test
    fun `needsRetry triggers only when fewer than 2 dishes clear the fit bar`() {
        assertTrue(MacroMateAgent.needsRetry(emptyList()))
        assertTrue(MacroMateAgent.needsRetry(listOf(dishWithScore(0.9))))
        assertTrue(MacroMateAgent.needsRetry(listOf(dishWithScore(0.3), dishWithScore(0.2))))
        assertEquals(false, MacroMateAgent.needsRetry(listOf(dishWithScore(0.9), dishWithScore(0.5))))
    }

    // Regression: a 42g-protein dish on a fresh day used to score ~42% because it was
    // compared against the FULL daily budget. Against a per-meal target it must score high.
    @Test
    fun `high-protein dish scores well on a fresh day`() = runTest {
        val profile = UserProfile() // daily: 2000 kcal / 130P / 220C / 65F

        val states = MacroMateAgent.searchAndRankMeals(
            query = "protein",
            profile = profile,
            remainingCalories = profile.dailyCalories,
            remainingProtein = profile.dailyProteinG,
            remainingCarbs = profile.dailyCarbsG,
            remainingFat = profile.dailyFatG
        ).toList()

        val success = states.filterIsInstance<AgentState.Success>().single()
        assertTrue("Expected ranked results", success.results.isNotEmpty())
        val top = success.results.first()
        assertTrue(
            "Top dish '${top.name}' scored ${(top.score * 100).toInt()}%, expected > 70%",
            top.score > 0.7
        )
    }
}
