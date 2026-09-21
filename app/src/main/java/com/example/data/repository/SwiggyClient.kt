package com.example.data.repository

import android.util.Log
import com.example.data.model.DishItem
import com.example.data.model.Restaurant
import java.util.Locale

object SwiggyClient {
    private const val TAG = "SwiggyClient"

    private val restaurantsList = listOf(
        Restaurant(
            id = "rest_1",
            name = "EatFit - Healthy Bowls",
            rating = 4.4,
            cuisines = listOf("Healthy", "North Indian", "Keto", "Salads"),
            deliveryTimeMin = 22,
            averagePrice = 250.0
        ),
        Restaurant(
            id = "rest_2",
            name = "Punjab Bistro - High Protein",
            rating = 4.2,
            cuisines = listOf("North Indian", "Tandoor", "Keto friendly"),
            deliveryTimeMin = 35,
            averagePrice = 350.0
        ),
        Restaurant(
            id = "rest_3",
            name = "The Salad Company",
            rating = 4.5,
            cuisines = listOf("Continental", "Healthy", "Vegan", "Gluten-Free"),
            deliveryTimeMin = 18,
            averagePrice = 300.0
        ),
        Restaurant(
            id = "rest_4",
            name = "Dakshin Flavors - High Protein South",
            rating = 4.3,
            cuisines = listOf("South Indian", "Health Eats", "Vegetarian"),
            deliveryTimeMin = 25,
            averagePrice = 180.0
        )
    )

    private val menuData = mapOf(
        "rest_1" to listOf(
            DishItem(
                id = "dish_1_1",
                name = "High-Protein Paneer Rice Bowl",
                description = "Low-fat paneer chunks cooked in rich homestyle gravy, served with high-protein brown rice, edamame and steamed broccoli.",
                price = 249.0,
                proteinG = 25,
                carbsG = 45,
                fatG = 12,
                calories = 390,
                deliveryTimeMin = 22,
                restaurantName = "EatFit - Healthy Bowls",
                restaurantId = "rest_1"
            ),
            DishItem(
                id = "dish_1_2",
                name = "Keto Herb Chicken Salad",
                description = "Grilled herb-infused chicken breast, sliced avocados, roasted almonds, leafy greens, tossed with high-fiber sugar-free olive dressing.",
                price = 289.0,
                proteinG = 38,
                carbsG = 8,
                fatG = 18,
                calories = 346,
                deliveryTimeMin = 22,
                restaurantName = "EatFit - Healthy Bowls",
                restaurantId = "rest_1"
            ),
            DishItem(
                id = "dish_1_3",
                name = "Oats & Lentil Diet Khichdi",
                description = "Slow-cooked combination of rolled oats and split yellow moong lentils with antioxidant-rich spinach, carrots, and light cow ghee.",
                price = 199.0,
                proteinG = 14,
                carbsG = 38,
                fatG = 6,
                calories = 262,
                deliveryTimeMin = 22,
                restaurantName = "EatFit - Healthy Bowls",
                restaurantId = "rest_1"
            ),
            DishItem(
                id = "dish_1_4",
                name = "Double Protein Chickpea Salad",
                description = "Steamed kabuli chana, bean sprouts, diced cucumbers, tomatoes, loaded with roasted peanuts and lemon-mint fat-burning dressing.",
                price = 179.0,
                proteinG = 18,
                carbsG = 40,
                fatG = 7,
                calories = 295,
                deliveryTimeMin = 22,
                restaurantName = "EatFit - Healthy Bowls",
                restaurantId = "rest_1"
            ),
            DishItem(
                id = "dish_1_5",
                name = "Keto Sugar-Free Chocolate Pot",
                description = "Rich, sugar-free dark cocoa dessert whipped with almond milk and avocado paste. A heavenly macro-friendly vegan sweet treat.",
                price = 149.0,
                proteinG = 5,
                carbsG = 11,
                fatG = 15,
                calories = 199,
                deliveryTimeMin = 22,
                restaurantName = "EatFit - Healthy Bowls",
                restaurantId = "rest_1"
            )
        ),
        "rest_2" to listOf(
            DishItem(
                id = "dish_2_1",
                name = "Tandoori Chicken Tikka Large",
                description = "Juicy skinless chicken thigh chunks classic marinated in protein curd yogurt, ginger, garlic, and special tandoor spices, charcoal smoked.",
                price = 349.0,
                proteinG = 42,
                carbsG = 4,
                fatG = 14,
                calories = 310,
                deliveryTimeMin = 35,
                restaurantName = "Punjab Bistro - High Protein",
                restaurantId = "rest_2"
            ),
            DishItem(
                id = "dish_2_2",
                name = "Low-Fat Butter Chicken (Keto)",
                description = "Tender shredded tandoori chicken simmered in cashew-tomato curry without sugar, heavy cream replaced with light skimmed milk cream.",
                price = 389.0,
                proteinG = 32,
                carbsG = 9,
                fatG = 16,
                calories = 308,
                deliveryTimeMin = 35,
                restaurantName = "Punjab Bistro - High Protein",
                restaurantId = "rest_2"
            ),
            DishItem(
                id = "dish_2_3",
                name = "Diet Paneer Bhurji with Peas",
                description = "Freshly crumbled low-fat house paneer sautéed with diced onions, tomatoes, sweet green peas and organic yellow turmeric. Loaded with vegetarian muscle-building protein.",
                price = 299.0,
                proteinG = 22,
                carbsG = 12,
                fatG = 14,
                calories = 262,
                deliveryTimeMin = 35,
                restaurantName = "Punjab Bistro - High Protein",
                restaurantId = "rest_2"
            ),
            DishItem(
                id = "dish_2_4",
                name = "Protein Loaded Soya Chaap Tikka",
                description = "High protein soya chunks skewered and grilled with bell peppers and onion rings with a punchy mint yogurt dip.",
                price = 249.0,
                proteinG = 28,
                carbsG = 22,
                fatG = 10,
                calories = 290,
                deliveryTimeMin = 35,
                restaurantName = "Punjab Bistro - High Protein",
                restaurantId = "rest_2"
            ),
            DishItem(
                id = "dish_2_5",
                name = "Multi-Grain Roti (Pack of 2)",
                description = "High-fiber warm rotis prepared from equal parts whole wheat, finger millet ragi, sorghum jowar, and organic oats flour.",
                price = 45.0,
                proteinG = 6,
                carbsG = 26,
                fatG = 2,
                calories = 146,
                deliveryTimeMin = 35,
                restaurantName = "Punjab Bistro - High Protein",
                restaurantId = "rest_2"
            )
        ),
        "rest_3" to listOf(
            DishItem(
                id = "dish_3_1",
                name = "High-Protein Tofu Crunch Salad",
                description = "Crispy dynamic pan-fried firm tofu, green edamame, red bell peppers, chia seeds, tossed with natural organic dynamic flax oil sesame vinaigrette. High nutrition, low carbs.",
                price = 299.0,
                proteinG = 23,
                carbsG = 12,
                fatG = 12,
                calories = 248,
                deliveryTimeMin = 18,
                restaurantName = "The Salad Company",
                restaurantId = "rest_3"
            ),
            DishItem(
                id = "dish_3_2",
                name = "Mediterranean Roasted Falafel Bowl",
                description = "Fiber-rich crispy baked chickpeas falafels, fresh cucumbers, organic quinoa, high-calcium hummus dip, wild greens, oil-free.",
                price = 329.0,
                proteinG = 12,
                carbsG = 44,
                fatG = 9,
                calories = 305,
                deliveryTimeMin = 18,
                restaurantName = "The Salad Company",
                restaurantId = "rest_3"
            ),
            DishItem(
                id = "dish_3_3",
                name = "Athletic Greek Chicken Breast Bowl",
                description = "Lean steam-cooked double chicken breast slices, low-lipid feta cheese, high-fiber kalamata olives, cherry tomatoes on organic salad leaves bedding.",
                price = 349.0,
                proteinG = 44,
                carbsG = 6,
                fatG = 11,
                calories = 299,
                deliveryTimeMin = 18,
                restaurantName = "The Salad Company",
                restaurantId = "rest_3"
            ),
            DishItem(
                id = "dish_3_4",
                name = "Clean Avocado Keto Superfood Wrap",
                description = "Low-carb wheat bran tortilla containing freshly crushed ripe avocado, spinach, steamed eggs, low-fat sour cream dynamic spice mix.",
                price = 279.0,
                proteinG = 15,
                carbsG = 14,
                fatG = 16,
                calories = 260,
                deliveryTimeMin = 18,
                restaurantName = "The Salad Company",
                restaurantId = "rest_3"
            ),
            DishItem(
                id = "dish_3_5",
                name = "Fiber-Rich Quinoa Fruit Medley",
                description = "Cooked dynamic white organic quinoa mixed with seasonal organic pomegranates, green apples, roasted walnuts and high-antioxidant honey drizzle.",
                price = 229.0,
                proteinG = 8,
                carbsG = 36,
                fatG = 5,
                calories = 221,
                deliveryTimeMin = 18,
                restaurantName = "The Salad Company",
                restaurantId = "rest_3"
            )
        ),
        "rest_4" to listOf(
            DishItem(
                id = "dish_4_1",
                name = "Ragi Steamed Protein Idli",
                description = "Pre-fermented multi-grain finger millet and black gram batter steamed into flat idli cakes, served with protein peanut subzi and low-sodium sambar.",
                price = 149.0,
                proteinG = 9,
                carbsG = 34,
                fatG = 3,
                calories = 199,
                deliveryTimeMin = 25,
                restaurantName = "Dakshin Flavors - High Protein South",
                restaurantId = "rest_4"
            ),
            DishItem(
                id = "dish_4_2",
                name = "Sprouted Moong Diet Dosa",
                description = "Super crispy flat thin pancake spread strictly using green moong dal sprouts batter, loaded with freshly minced dynamic ginger-chili paste and organic coriander.",
                price = 169.0,
                proteinG = 15,
                carbsG = 28,
                fatG = 4,
                calories = 208,
                deliveryTimeMin = 25,
                restaurantName = "Dakshin Flavors - High Protein South",
                restaurantId = "rest_4"
            ),
            DishItem(
                id = "dish_4_3",
                name = "Low-Oil Paneer & Peas Uttapam",
                description = "Savory thick rice-lentil pancake topped generously with high-calcium low-fat shredded paneer, raw onions, green bell peppers, fresh mint, lightly pan-grilled.",
                price = 189.0,
                proteinG = 18,
                carbsG = 32,
                fatG = 8,
                calories = 272,
                deliveryTimeMin = 25,
                restaurantName = "Dakshin Flavors - High Protein South",
                restaurantId = "rest_4"
            ),
            DishItem(
                id = "dish_4_4",
                name = "High-Fiber Sambar Vada (Diet Style)",
                description = "High protein whole black lentils doughnut vadas, air-fried instead of deep fried, immersed in authentic steaming tamarind vegetable sambar.",
                price = 129.0,
                proteinG = 11,
                carbsG = 24,
                fatG = 3,
                calories = 167,
                deliveryTimeMin = 25,
                restaurantName = "Dakshin Flavors - High Protein South",
                restaurantId = "rest_4"
            ),
            DishItem(
                id = "dish_4_5",
                name = "Ginger-Mint Buttermilk (Active Core)",
                description = "Rehydrating organic cow-curd yogurt whey whisked with fresh dynamic ginger juice, crushed cooling mint leaves, and light digestive cumin powder.",
                price = 79.0,
                proteinG = 4,
                carbsG = 6,
                fatG = 2,
                calories = 58,
                deliveryTimeMin = 25,
                restaurantName = "Dakshin Flavors - High Protein South",
                restaurantId = "rest_4"
            )
        )
    )

    /** True when results come from the live Swiggy MCP server instead of mock data. */
    fun isLive(): Boolean = SwiggyMcpClient.isConnected()

    // TOOL 1 — searchRestaurants (live Swiggy MCP when connected, mock fallback)
    suspend fun searchRestaurants(lat: Double = 12.9716, lng: Double = 77.5946, query: String, limit: Int = 4): List<Restaurant> {
        if (isLive()) {
            try {
                val live = SwiggyMcpClient.searchRestaurants(query)
                if (live.isNotEmpty()) return live.take(limit)
            } catch (e: Exception) {
                Log.w(TAG, "Live Swiggy search failed, falling back to mock", e)
            }
        }
        val lowerQuery = query.lowercase(Locale.ROOT)
        return restaurantsList.filter { restaurant ->
            restaurant.name.lowercase(Locale.ROOT).contains(lowerQuery) ||
            restaurant.cuisines.any { c -> c.lowercase(Locale.ROOT).contains(lowerQuery) }
        }.take(limit).ifEmpty {
            restaurantsList.take(limit)
        }
    }

    // TOOL 2 — getMenuAndEstimateMacros (live MCP menu + macro estimation, mock fallback)
    suspend fun getMenu(restaurantId: String): List<DishItem> {
        menuData[restaurantId]?.let { return it }
        if (isLive()) {
            try {
                return SwiggyMcpClient.getMenu(restaurantId)
            } catch (e: Exception) {
                Log.w(TAG, "Live Swiggy menu fetch failed for $restaurantId", e)
            }
        }
        return emptyList()
    }
}
