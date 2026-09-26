package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_account")
data class UserAccount(
    @PrimaryKey val email: String,
    val passwordHash: String,
    val name: String,
    val hasCompletedOnboarding: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val userId: String = "default_user",
    val name: String = "",
    val age: Int = 25,
    val weightKg: Double = 70.0,
    val heightCm: Double = 175.0,
    val goal: String = "Maintain", // Cut, Maintain, Bulk
    val dailyCalories: Int = 2000,
    val dailyProteinG: Int = 130,
    val dailyCarbsG: Int = 220,
    val dailyFatG: Int = 65,
    val allergiesJson: String = "[]", // vegetarian, vegan, gluten-free, nut allergy, dairy-free
    val cuisinePrefsJson: String = "[]", // north Indian, south Indian, continental, keto
    val llmProvider: String = "Gemini", // Gemini, Claude
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "macro_log")
data class MacroLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "default_user",
    val dateString: String = "", // YYYY-MM-DD
    val name: String = "",
    val restaurant: String = "",
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val calories: Int = 0,
    val price: Double = 0.0,
    val source: String = "swiggy", // swiggy / manual
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "order_history")
data class OrderHistory(
    @PrimaryKey val orderId: String = "",
    val userId: String = "default_user",
    val itemsJson: String = "[]", // List of items
    val totalProtein: Int = 0,
    val totalCarbs: Int = 0,
    val totalFat: Int = 0,
    val totalCalories: Int = 0,
    val totalPrice: Double = 0.0,
    val swiggyOrderId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// UI and Network Helper Models
data class DishItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val calories: Int = 0,
    val deliveryTimeMin: Int = 30,
    val restaurantName: String = "",
    val restaurantId: String = "",
    val imageResUrl: String = "",
    val aiReasoning: String = "",
    val score: Double = 0.0
)

data class Restaurant(
    val id: String,
    val name: String,
    val rating: Double,
    val cuisines: List<String>,
    val deliveryTimeMin: Int,
    val averagePrice: Double
)

data class CartItem(
    val dish: DishItem,
    var quantity: Int
)

data class SwiggyAddress(
    val id: String,
    val label: String,
    val line: String
)

enum class MealGoalType {
    HIGH_PROTEIN,
    KETO_FAT_LOSS,
    LEAN_MASS_BULK,
    LOW_CALORIE_CLEAN,
    QUICK_HYDRATION_SNACK
}

enum class BudgetTier {
    BUDGET_UNDER_200,
    STANDARD_UNDER_400,
    PREMIUM_UNRESTRICTED
}

/**
 * Output of the on-device "System 1" decision engine ([LayaDecisionEngine]). [usedOnnxModel]
 * is true only when the bundled laya_decision.onnx actually ran inference — false means the
 * deterministic rule-based fallback answered instead (model/vocab missing, or inference
 * threw), so callers/UI can report which path actually produced a given decision honestly.
 */
data class SystemOneDecision(
    val goal: MealGoalType,
    val budget: BudgetTier,
    val searchKeyword: String,
    val isVegetarianForced: Boolean,
    val confidence: Float,
    val latencyMs: Long,
    val usedOnnxModel: Boolean,
    /** The specific dish/food the user named (e.g. "burger"), if any — searched for before falling back to the goal. */
    val requestedFood: String? = null
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String = "AI", // "USER" or "AI" or "SYSTEM" or "THINKING"
    val content: String,
    val reasoningSteps: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val results: List<DishItem> = emptyList() // If any results are attached to this message
)
