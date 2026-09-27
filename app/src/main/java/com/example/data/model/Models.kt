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
    val createdAt: Long = System.currentTimeMillis(),
    // --- Dietary Log + Monthly Budget (schema v3, extended in v4) ---
    val monthlyBudgetInr: Double = 0.0,     // 0 = no budget set; budget features stay off
    val mealsPerDay: Int = 4,               // 2..6, user preference (meal count doesn't change outcomes)
    val wakeTimeMinutes: Int = 420,         // minutes after midnight (07:00)
    val sleepTimeMinutes: Int = 1380,       // 23:00
    val workoutTimeMinutes: Int = 1080,     // 18:00; only used when workoutEnabled
    val isEarlyHeavyCut: Boolean = false,   // front-loaded template: bigger breakfast, same kcal
    // v4
    val budgetStartDate: String = "",       // yyyy-MM-dd the budget was first set; "" = whole month
    val workoutEnabled: Boolean = false,    // false = no training anchor in the schedule
    val dayEndsAtHour: Int = 4,             // meals before this hour count toward the previous day
    val remindersEnabled: Boolean = false,
    // Optional health check: any of these pauses carry-over and fasting nudges (see SafetyRules)
    val isPregnantOrLactating: Boolean = false,
    val onDiabetesMeds: Boolean = false,
    val edHistory: Boolean = false,
    // v6
    val sex: String = ""                    // "Male" | "Female" | "" (not set: older accounts)
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
    val timestamp: Long = System.currentTimeMillis(),
    val mealSlot: String? = null,  // BREAKFAST, LUNCH, ... ; null = assign by time of day
    val isShared: Boolean = false  // ordered for someone else: price counts toward spend, macros don't
)

/**
 * One row per (user, logical day). Snapshots the targets in force that day so later goal
 * changes never rewrite history, and records what the user told us about the day.
 * Replaces v3's daily_target_snapshot, whose primary key (date only) mixed users up.
 */
@Entity(tableName = "day_record", primaryKeys = ["userId", "dateString"])
data class DayRecord(
    val userId: String,
    val dateString: String,
    val status: String = DayStatus.AUTO.name,  // AUTO = derive from logs; or FASTED / ESTIMATED / COMPLETE / SKIPPED
    val estimatedKcal: Int? = null,            // set when status = ESTIMATED
    val targetKcal: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int,
    val goal: String,
    val noSpendConfirmed: Boolean = false,     // user confirmed no food spend outside the app that day
    val waterGlasses: Int = 0                  // glasses of water ticked that day
)

/** What the user has told us about a day. AUTO means "classify from the logged meals". */
enum class DayStatus { AUTO, COMPLETE, FASTED, ESTIMATED, SKIPPED }

@Entity(tableName = "weight_log")
data class WeightLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "default_user",
    val date: String = "", // YYYY-MM-DD
    val weightKg: Double = 70.0,
    val trendWeightKg: Double = 70.0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Food spend the app can't see on its own: outside orders, Swiggy fees/taxes, month-end reconcile. */
@Entity(tableName = "outside_spend_log")
data class OutsideSpendLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "default_user",
    val date: String = "", // YYYY-MM-DD
    val amountInr: Double = 0.0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val kind: String = SpendKind.OUTSIDE_ORDER.name // v4: OUTSIDE_ORDER | FEES | RECONCILE
)

enum class SpendKind { OUTSIDE_ORDER, FEES, RECONCILE }

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
    var quantity: Int,
    val isShared: Boolean = false, // ordered for someone else: counts toward spend, not macros
    val mealSlot: String? = null   // Dietary Log slot this dish was searched for (logged into it on order)
)

/** The Dietary Log slot a chat search was started from ("Find on Swiggy"). */
data class MealSlotContext(val slot: String, val label: String)

data class SwiggyAddress(
    val id: String,
    val label: String,
    val line: String,
    val phone: String = ""
)

/** A device-location fix reverse-geocoded into the fields Swiggy's create_address needs. */
data class ResolvedAddress(
    val fullAddress: String,
    val addressLine: String,
    val addressLine2: String,
    val locality: String,
    val city: String,
    val postalCode: String,
    val latitude: Double,
    val longitude: Double
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
