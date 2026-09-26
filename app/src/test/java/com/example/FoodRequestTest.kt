package com.example

import com.example.data.repository.MacroMateAgent
import com.example.data.repository.SwiggyClient
import com.example.data.repository.extractFoodTerm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRequestTest {

    @Test
    fun namedFoodIsKeptAsTheSearchTerm() {
        assertEquals("burger", extractFoodTerm("burgers"))
        assertEquals("burger", extractFoodTerm("I want burgers please"))
        assertEquals("chicken burger", extractFoodTerm("chicken burger under 250"))
        assertEquals("paneer tikka", extractFoodTerm("paneer tikka high protein"))
        assertEquals("pizza", extractFoodTerm("veg pizza"))
        assertEquals("salad", extractFoodTerm("low calorie salad for weight loss under 200"))
    }

    @Test
    fun goalOnlyRequestsHaveNoNamedFood() {
        listOf(
            "High protein lunch under ₹300", "Post-workout dinner", "Keto breakfast",
            "Bulk meal 800+ cal", "high protein lunch under 300 rupees"
        ).forEach { assertNull("'$it' should not name a food", extractFoodTerm(it)) }
    }

    @Test
    fun strictSearchNeverSilentlyBroadens() = runBlocking {
        assertTrue(SwiggyClient.searchRestaurants(query = "burger", strict = true).any { it.name.contains("Burger") })
        assertTrue(SwiggyClient.searchRestaurants(query = "sushi", strict = true).isEmpty())
        assertTrue(SwiggyClient.searchRestaurants(query = "sushi", strict = false).isNotEmpty())
    }

    @Test
    fun burgerMenuItemsMatchABurgerRequest() = runBlocking {
        val menu = SwiggyClient.getMenu("rest_5")
        assertTrue(menu.isNotEmpty())
        assertTrue(menu.all { MacroMateAgent.dishMatchesFood(it, "burger") })
        assertTrue(SwiggyClient.getMenu("rest_2").none { MacroMateAgent.dishMatchesFood(it, "burger") })
    }
}
