package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MacroMateDatabase
import com.example.data.model.*
import android.content.Intent
import android.net.Uri
import com.example.data.repository.AgentState
import com.example.data.repository.GeminiNanoClient
import com.example.data.repository.MacroMateRepository
import com.example.data.repository.NanoFoodEstimate
import com.example.data.repository.RestGeminiClient
import com.example.data.repository.SwiggyClient
import com.example.data.repository.SwiggyMcpClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class MacroMateViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MacroMateDatabase.getDatabase(application)
    private val repository = MacroMateRepository(database.dao())

    // --- Swiggy MCP Connection State ---
    private val _swiggyLinked = MutableStateFlow(false)
    val swiggyLinked: StateFlow<Boolean> = _swiggyLinked.asStateFlow()

    private val _swiggyStatus = MutableStateFlow<String?>(null)
    val swiggyStatus: StateFlow<String?> = _swiggyStatus.asStateFlow()

    private val _activeSwiggyAddress = MutableStateFlow<String?>(null)
    val activeSwiggyAddress: StateFlow<String?> = _activeSwiggyAddress.asStateFlow()

    private val _swiggyAddresses = MutableStateFlow<List<SwiggyAddress>>(emptyList())
    val swiggyAddresses: StateFlow<List<SwiggyAddress>> = _swiggyAddresses.asStateFlow()

    private val _swiggyAddressesHasMore = MutableStateFlow(false)
    val swiggyAddressesHasMore: StateFlow<Boolean> = _swiggyAddressesHasMore.asStateFlow()

    private val _swiggyAddressesLoading = MutableStateFlow(false)
    val swiggyAddressesLoading: StateFlow<Boolean> = _swiggyAddressesLoading.asStateFlow()

    private var swiggyAddressPage = 1

    init {
        SwiggyMcpClient.init(application)
        _swiggyLinked.value = SwiggyMcpClient.isConnected()
        _activeSwiggyAddress.value = SwiggyMcpClient.getActiveAddressDisplay()
    }

    fun connectSwiggy() {
        viewModelScope.launch {
            _swiggyStatus.value = "Complete the Swiggy login (phone + OTP) in your browser..."
            val result = SwiggyMcpClient.connect { authUrl ->
                getApplication<Application>().startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            _swiggyLinked.value = SwiggyMcpClient.isConnected()
            _activeSwiggyAddress.value = SwiggyMcpClient.getActiveAddressDisplay()
            _swiggyStatus.value = result.fold(
                onSuccess = { "Connected! The concierge now searches real Swiggy restaurants." },
                onFailure = { it.message ?: "Connection failed. Try again." }
            )
        }
    }

    fun disconnectSwiggy() {
        SwiggyMcpClient.disconnect()
        _swiggyLinked.value = false
        _swiggyStatus.value = null
        _activeSwiggyAddress.value = null
        _swiggyAddresses.value = emptyList()
    }

    /** Loads (or reloads) the saved-address picker list. [reset] starts back at page 1. */
    fun loadSwiggyAddresses(reset: Boolean = true) {
        if (!SwiggyMcpClient.isConnected()) return
        viewModelScope.launch {
            if (reset) {
                swiggyAddressPage = 1
                _swiggyAddresses.value = emptyList()
            }
            _swiggyAddressesLoading.value = true
            try {
                val page = SwiggyMcpClient.fetchAddresses(swiggyAddressPage)
                _swiggyAddresses.value = _swiggyAddresses.value + page.addresses
                _swiggyAddressesHasMore.value = page.hasMore
                swiggyAddressPage++
            } catch (e: Exception) {
                _swiggyStatus.value = e.message ?: "Couldn't load your saved addresses."
            } finally {
                _swiggyAddressesLoading.value = false
            }
        }
    }

    /** Switches the active delivery address; subsequent restaurant searches resolve against it. */
    fun selectSwiggyAddress(address: SwiggyAddress) {
        SwiggyMcpClient.setActiveAddress(address)
        _activeSwiggyAddress.value = SwiggyMcpClient.getActiveAddressDisplay()
    }

    // --- SharedPreferences & Current User ---
    private val prefs = application.getSharedPreferences("macromate_prefs", Context.MODE_PRIVATE)
    private val _currentUserId = MutableStateFlow(prefs.getString("logged_in_user_email", null))
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    // --- User Profile State ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val userProfile: StateFlow<UserProfile?> = _currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                repository.getUserProfile(userId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Macro Log State (Today) ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayLogs: StateFlow<List<MacroLog>> = _currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                repository.getTodayMacroLogs(userId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- All Historic Logs ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allLogs: StateFlow<List<MacroLog>> = _currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                repository.getAllMacroLogs(userId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Order History State ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val orderHistory: StateFlow<List<OrderHistory>> = _currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                repository.getOrderHistory(userId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Chat & Agent UI State ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "AI",
                content = "Hello there! I'm MacroMate, your personalized nutritional concierge powered by Swiggy. Let me know what you'd like to eat, or click a quick prompt below to find meals matching your macro goals!"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAgentThinking = MutableStateFlow(value = false)
    val isAgentThinking: StateFlow<Boolean> = _isAgentThinking.asStateFlow()

    private val _thinkingSteps = MutableStateFlow<List<String>>(emptyList())
    val thinkingSteps: StateFlow<List<String>> = _thinkingSteps.asStateFlow()

    // Active screen meal results (if any), swipe-to-dismiss active state
    private val _activeResults = MutableStateFlow<List<DishItem>>(emptyList())
    val activeResults: StateFlow<List<DishItem>> = _activeResults.asStateFlow()

    // --- Cart State ---
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    // --- Auth States & Operations ---
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun clearAuthError() {
        _authError.value = null
    }

    fun signIn(email: String, passwordText: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _authError.value = null
            val trimmedEmail = email.trim().lowercase()
            if (trimmedEmail.isBlank() || passwordText.isBlank()) {
                _authError.value = "All fields are required."
                onResult(false)
                return@launch
            }
            val account = repository.getUserAccount(trimmedEmail)
            if (account == null) {
                _authError.value = "Account not found. Please register."
                onResult(false)
            } else if (account.passwordHash != passwordText) {
                _authError.value = "Incorrect password. Try again."
                onResult(false)
            } else {
                prefs.edit { putString("logged_in_user_email", account.email) }
                _currentUserId.value = account.email
                onResult(true)
            }
        }
    }

    fun signUp(nameText: String, email: String, passwordText: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _authError.value = null
            val trimmedName = nameText.trim()
            val trimmedEmail = email.trim().lowercase()
            if (trimmedName.isBlank() || trimmedEmail.isBlank() || passwordText.isBlank()) {
                _authError.value = "All fields are required."
                onResult(false)
                return@launch
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                _authError.value = "Please enter a valid email address."
                onResult(false)
                return@launch
            }
            if (passwordText.length < 6) {
                _authError.value = "Password must be at least 6 characters."
                onResult(false)
                return@launch
            }

            val existing = repository.getUserAccount(trimmedEmail)
            if (existing != null) {
                _authError.value = "An account with this email already exists."
                onResult(false)
            } else {
                val newAccount = UserAccount(
                    email = trimmedEmail,
                    passwordHash = passwordText,
                    name = trimmedName
                )
                repository.saveUserAccount(newAccount)
                prefs.edit().putString("logged_in_user_email", trimmedEmail).apply()
                _currentUserId.value = trimmedEmail

                // Set initial blank profile for this user so onboarding triggers if needed
                val newProfile = UserProfile(
                    userId = trimmedEmail,
                    name = trimmedName,
                    weightKg = 0.0, // indicating empty/incomplete onboarding!
                    dailyCalories = 2000,
                    dailyProteinG = 130,
                    dailyCarbsG = 220,
                    dailyFatG = 65,
                    allergiesJson = "[]",
                    cuisinePrefsJson = "[]"
                )
                repository.saveUserProfile(newProfile)
                onResult(true)
            }
        }
    }

    fun logout() {
        prefs.edit { remove("logged_in_user_email") }
        _currentUserId.value = null
        _chatMessages.value = listOf(
            ChatMessage(
                sender = "AI",
                content = "Hello there! I'm MacroMate, your personalized nutritional concierge powered by Swiggy. Let me know what you'd like to eat, or click a quick prompt below to find meals matching your macro goals!",
            )
        )
        _activeResults.value = emptyList()
        clearCart()
    }



    // --- Profile Calculations & Profile Saving ---
    /**
     * Weight-based calorie auto-calculator:
     * TDEE Baseline = weight * 30
     * Cut: TDEE - 500, Maintain: TDEE, Bulk: TDEE + 400
     */
    fun calculateMacrosForGoal(weightKg: Double, goal: String): QuadrupleCalories {
        val tdee = (weightKg * 30.0).toInt()
        val cals = when (goal) {
            "Cut" -> maxOf(1200, tdee - 500)
            "Bulk" -> tdee + 400
            else -> tdee // Maintain
        }
        val protein = maxOf(40, (weightKg * 1.8).toInt())
        val fat = maxOf(30, (cals * 0.25 / 9.0).toInt())
        val carbs = maxOf(80, (cals - (protein * 4) - (fat * 9)) / 4)

        return QuadrupleCalories(cals, protein, carbs, fat)
    }

    data class QuadrupleCalories(val calories: Int, val protein: Int, val carbs: Int, val fat: Int)

    fun saveProfile(
        name: String,
        age: Int,
        weight: Double,
        height: Double,
        goal: String,
        allergies: List<String>,
        cuisinePrefs: List<String>,
        useManualMacros: Boolean = false,
        manualCalories: Int = 2000,
        manualProtein: Int = 130,
        manualCarbs: Int = 220,
        manualFat: Int = 65
    ) {
        viewModelScope.launch {
            val userId = _currentUserId.value ?: "default_user"
            val macros = if (useManualMacros) {
                QuadrupleCalories(manualCalories, manualProtein, manualCarbs, manualFat)
            } else {
                calculateMacrosForGoal(weight, goal)
            }

            val updatedProfile = UserProfile(
                userId = userId,
                name = name,
                age = age,
                weightKg = weight,
                heightCm = height,
                goal = goal,
                dailyCalories = macros.calories,
                dailyProteinG = macros.protein,
                dailyCarbsG = macros.carbs,
                dailyFatG = macros.fat,
                allergiesJson = JSONArray(allergies).toString(),
                cuisinePrefsJson = JSONArray(cuisinePrefs).toString(),
                llmProvider = userProfile.value?.llmProvider ?: "Gemini"
            )
            repository.saveUserProfile(updatedProfile)
        }
    }

    fun setLlmProvider(provider: String) {
        viewModelScope.launch {
            userProfile.value?.let { current ->
                repository.saveUserProfile(current.copy(llmProvider = provider))
            }
        }
    }

    // --- Remaining Core Calculations for Macro Tracker ---
    fun getConsumedMacros(): ConsumedMacros {
        val list = todayLogs.value
        val cals = list.sumOf { it.calories }
        val protein = list.sumOf { it.proteinG }
        val carbs = list.sumOf { it.carbsG }
        val fat = list.sumOf { it.fatG }
        return ConsumedMacros(cals, protein, carbs, fat)
    }

    data class ConsumedMacros(val calories: Int, val protein: Int, val carbs: Int, val fat: Int)

    // --- Chat UI & ReAct Agent Simulation/Online Handling ---
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // 1. Append User Message
        val userMsg = ChatMessage(sender = "USER", content = text)
        _chatMessages.update { oldList -> oldList + userMsg }

        // Start agent processing
        viewModelScope.launch {
            _isAgentThinking.value = true
            _thinkingSteps.value = emptyList()
            _activeResults.value = emptyList()

            val profile = userProfile.value ?: UserProfile()
            val consumed = getConsumedMacros()

            val remCal = Math.max(0, profile.dailyCalories - consumed.calories)
            val remP = Math.max(0, profile.dailyProteinG - consumed.protein)
            val remC = Math.max(0, profile.dailyCarbsG - consumed.carbs)
            val remF = Math.max(0, profile.dailyFatG - consumed.fat)

            repository.runAgent(
                query = text,
                profile = profile,
                remainingCalories = remCal,
                remainingProtein = remP,
                remainingCarbs = remC,
                remainingFat = remF
            ).collect { agentState ->
                when (agentState) {
                    is AgentState.Thinking -> {
                        _thinkingSteps.update { steps -> steps + agentState.step }
                    }
                    is AgentState.Success -> {
                        _isAgentThinking.value = false
                        val currentThinking = _thinkingSteps.value

                        // Generate beautiful AI markdown detailing reasoning to render
                        val cleanMessageContent = generateHtmlLikeResponse(agentState.message, agentState.results)

                        val aiMessage = ChatMessage(
                            sender = "AI",
                            content = cleanMessageContent,
                            reasoningSteps = currentThinking,
                            results = agentState.results
                        )
                        _chatMessages.update { oldList -> oldList + aiMessage }
                        _activeResults.value = agentState.results
                    }
                    is AgentState.Error -> {
                        _isAgentThinking.value = false
                        val aiErrMessage = ChatMessage(
                            sender = "AI",
                            content = "Apologies, I encountered an operational challenge while scoring the Swiggy interface: ${agentState.message}"
                        )
                        _chatMessages.update { oldList -> oldList + aiErrMessage }
                    }
                }
            }
        }
    }

    private fun generateHtmlLikeResponse(baseMsg: String, results: List<DishItem>): String {
        if (results.isEmpty()) return baseMsg
        val sb = StringBuilder()
        sb.append(baseMsg).append("\n\nHere are the customized dishes matching your specs:\n")
        results.forEachIndexed { i, dish ->
            sb.append("\n${i + 1}. **${dish.name}** [₹${dish.price}]\n")
            sb.append("   - **Macros**: P: ${dish.proteinG}g · C: ${dish.carbsG}g · F: ${dish.fatG}g · ${dish.calories} kcal\n")
            sb.append("   - *AI Feedback*: ${dish.aiReasoning.take(130)}...\n")
        }
        return sb.toString()
    }

    fun dismissResult(dishId: String) {
        _activeResults.update { current -> current.filter { it.id != dishId } }
    }

    fun clearResults() {
        _activeResults.value = emptyList()
    }

    // --- Cart Actions ---
    fun addItemToCart(dish: DishItem) {
        _cart.update { current ->
            val existing = current.find { it.dish.id == dish.id }
            if (existing != null) {
                current.map { if (it.dish.id == dish.id) it.copy(quantity = it.quantity + 1) else it }
            } else {
                current + CartItem(dish, 1)
            }
        }
    }

    fun removeCartItem(dishId: String) {
        _cart.update { current ->
            current.filter { it.dish.id != dishId }
        }
    }

    fun updateCartQuantity(dishId: String, delta: Int) {
        _cart.update { current ->
            current.mapNotNull { item ->
                if (item.dish.id == dishId) {
                    val newQty = item.quantity + delta
                    if (newQty <= 0) null else item.copy(quantity = newQty)
                } else {
                    item
                }
            }
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    fun getCartMacros(): ConsumedMacros {
        var cals = 0
        var protein = 0
        var carbs = 0
        var fat = 0
        cart.value.forEach { item ->
            val q = item.quantity
            cals += item.dish.calories * q
            protein += item.dish.proteinG * q
            carbs += item.dish.carbsG * q
            fat += item.dish.fatG * q
        }
        return ConsumedMacros(cals, protein, carbs, fat)
    }

    fun getCartTotal(): Double {
        return cart.value.sumOf { it.dish.price * it.quantity }
    }

    /** [onComplete] reports whether the cart was actually pushed to the user's real Swiggy
     *  cart (true) vs. only logged locally (false — not connected, or the sync call failed). */
    fun confirmCartAndPlaceOrder(onComplete: (syncedToSwiggy: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val items = cart.value
            if (items.isEmpty()) { onComplete(false); return@launch }

            val totalMacros = getCartMacros()
            val totalPrice = getCartTotal()
            val orderId = "MM-${100000 + Random().nextInt(899999)}"
            val swiggyId = "SWIGGY-${10000000 + Random().nextInt(89999999)}"

            // 1. Create items string description
            val itemsDescriptionList = items.map { "${it.quantity}x ${it.dish.name} (${it.dish.restaurantName})" }
            val itemsJson = JSONArray(itemsDescriptionList).toString()

            val userId = _currentUserId.value ?: "default_user"

            // 2. Insert into room order history
            val newOrder = OrderHistory(
                orderId = orderId,
                userId = userId,
                itemsJson = itemsJson,
                totalProtein = totalMacros.protein,
                totalCarbs = totalMacros.carbs,
                totalFat = totalMacros.fat,
                totalCalories = totalMacros.calories,
                totalPrice = totalPrice,
                swiggyOrderId = swiggyId,
                createdAt = System.currentTimeMillis()
            )
            repository.saveOrderHistory(newOrder)

            // 3. Log these items to today's macros
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            items.forEach { item ->
                val logs = MacroLog(
                    userId = userId,
                    dateString = todayStr,
                    name = item.dish.name,
                    restaurant = item.dish.restaurantName,
                    proteinG = item.dish.proteinG * item.quantity,
                    carbsG = item.dish.carbsG * item.quantity,
                    fatG = item.dish.fatG * item.quantity,
                    calories = item.dish.calories * item.quantity,
                    price = item.dish.price * item.quantity,
                    source = "swiggy"
                )
                repository.saveMacroLog(logs)
            }

            // 4. Push items into the user's real Swiggy cart so the deep-link opens a
            // populated cart instead of the app's home screen. Swiggy carts are single-
            // restaurant, so when items span restaurants, sync the largest group.
            var syncedToSwiggy = false
            if (SwiggyMcpClient.isConnected()) {
                val largestRestaurant = items.groupBy { it.dish.restaurantId }
                    .filterKeys { it.isNotBlank() }
                    .maxByOrNull { it.value.size }
                if (largestRestaurant != null) {
                    try {
                        SwiggyMcpClient.updateCart(
                            restaurantId = largestRestaurant.key,
                            restaurantName = largestRestaurant.value.first().dish.restaurantName,
                            items = largestRestaurant.value.map { it.dish.id to it.quantity }
                        )
                        syncedToSwiggy = true
                    } catch (e: Exception) {
                        Log.w("MacroMateViewModel", "Failed to sync cart to Swiggy", e)
                    }
                }
            }

            // 5. Clear Cart
            clearCart()

            _chatMessages.update { old ->
                old + ChatMessage(
                    sender = "SYSTEM",
                    content = if (syncedToSwiggy)
                        "🎉 Swiggy Cart Synced! Your items are in your real Swiggy cart, ready to check out. Meals logged to your daily Macro tracker."
                    else
                        "Order logged locally under ID: $swiggyId. Connect your Swiggy account in Profile to sync items to your real Swiggy cart. Meals logged to your daily Macro tracker."
                )
            }
            onComplete(syncedToSwiggy)
        }
    }

    // --- AI-Assisted Manual Meal Logging ---
    private val _isEstimatingMacros = MutableStateFlow(false)
    val isEstimatingMacros: StateFlow<Boolean> = _isEstimatingMacros.asStateFlow()

    data class FoodImageEstimate(val macros: ConsumedMacros, val suggestedName: String?)

    private val imagePrompt = "Identify the food in this photo and estimate its total nutrition for the visible portion."

    /**
     * Estimates macros from a photo of food, so logging a home-cooked or restaurant meal
     * doesn't require typing a description either — just a picture. Three tiers, tried in
     * order, each falling through to the next on any failure or unavailability:
     * 1. On-device Gemini Nano (private, free, works offline — if the device supports it)
     * 2. Cloud Gemini vision (if GEMINI_API_KEY is configured)
     * 3. null — caller falls back to manual entry, same contract as [estimateMacrosFromDescription]
     */
    fun estimateMacrosFromImage(bitmap: android.graphics.Bitmap, onResult: (FoodImageEstimate?) -> Unit) {
        viewModelScope.launch {
            _isEstimatingMacros.value = true
            val result = try {
                GeminiNanoClient.estimateMacrosFromImage(bitmap, imagePrompt)?.toFoodImageEstimate()
                    ?: estimateMacrosFromImageViaCloud(bitmap)
            } catch (e: Exception) {
                null
            }
            _isEstimatingMacros.value = false
            onResult(result)
        }
    }

    private suspend fun estimateMacrosFromImageViaCloud(bitmap: android.graphics.Bitmap): FoodImageEstimate? {
        if (!RestGeminiClient.isApiKeyAvailable()) return null
        val imageBytes = java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
            stream.toByteArray()
        }
        val json = RestGeminiClient.generateJsonFromImage(
            imageBytes = imageBytes,
            prompt = "$imagePrompt\n" +
                    "Return JSON: {\"foodName\": \"...\", \"calories\": 320, \"proteinG\": 20, \"carbsG\": 30, \"fatG\": 10}",
            systemInstruction = "You are a nutritionist estimating meal macros from a food photo. Respond with JSON only."
        )
        return json?.let {
            FoodImageEstimate(
                macros = ConsumedMacros(
                    calories = it.optInt("calories", 0),
                    protein = it.optInt("proteinG", 0),
                    carbs = it.optInt("carbsG", 0),
                    fat = it.optInt("fatG", 0)
                ),
                suggestedName = it.optString("foodName").takeIf { name -> name.isNotBlank() }
            )
        }
    }

    private fun NanoFoodEstimate.toFoodImageEstimate() = FoodImageEstimate(
        macros = ConsumedMacros(calories = calories, protein = proteinG, carbs = carbsG, fat = fatG),
        suggestedName = foodName?.takeIf { it.isNotBlank() }
    )

    /**
     * Estimates macros for a free-text food description (e.g. "2 idlis and sambar"), so manual
     * logging doesn't require the user to already know the exact macro numbers. Same three-tier
     * fallback as [estimateMacrosFromImage]: on-device Gemini Nano -> cloud Gemini -> null.
     */
    fun estimateMacrosFromDescription(description: String, onResult: (ConsumedMacros?) -> Unit) {
        if (description.isBlank()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            _isEstimatingMacros.value = true
            val prompt = "Estimate total nutrition for this food description: \"$description\". " +
                    "Assume typical single-serving portion sizes unless stated otherwise."
            val result = try {
                GeminiNanoClient.estimateMacrosFromText(prompt)?.let {
                    ConsumedMacros(calories = it.calories, protein = it.proteinG, carbs = it.carbsG, fat = it.fatG)
                } ?: estimateMacrosFromDescriptionViaCloud(prompt)
            } catch (e: Exception) {
                null
            }
            _isEstimatingMacros.value = false
            onResult(result)
        }
    }

    private suspend fun estimateMacrosFromDescriptionViaCloud(prompt: String): ConsumedMacros? {
        if (!RestGeminiClient.isApiKeyAvailable()) return null
        val json = RestGeminiClient.generateJson(
            prompt = "$prompt\nReturn JSON: {\"calories\": 320, \"proteinG\": 20, \"carbsG\": 30, \"fatG\": 10}",
            systemInstruction = "You are a nutritionist estimating meal macros from a plain-language description. Respond with JSON only."
        )
        return json?.let {
            ConsumedMacros(
                calories = it.optInt("calories", 0),
                protein = it.optInt("proteinG", 0),
                carbs = it.optInt("carbsG", 0),
                fat = it.optInt("fatG", 0)
            )
        }
    }

    // --- Manual Meal Logging ---
    fun logManualMeal(name: String, company: String, protein: Int, carbs: Int, fat: Int, calories: Int, cost: Double) {
        viewModelScope.launch {
            val userId = _currentUserId.value ?: "default_user"
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val log = MacroLog(
                userId = userId,
                dateString = todayStr,
                name = name,
                restaurant = company.ifBlank { "Home / External" },
                proteinG = protein,
                carbsG = carbs,
                fatG = fat,
                calories = calories,
                price = cost,
                source = "manual"
            )
            repository.saveMacroLog(log)
        }
    }

    fun deleteLoggedMeal(id: Int) {
        viewModelScope.launch {
            repository.deleteMacroLogById(id)
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            val userId = _currentUserId.value ?: "default_user"
            repository.clearMacroLogs(userId)
            repository.clearOrderHistory(userId)
            _chatMessages.value = listOf(
                ChatMessage(
                    sender = "AI",
                    content = "Welcome back! MacroMate is fully cleared. All local logs and cache have been wiped."
                )
            )
            _activeResults.value = emptyList()
            clearCart()
        }
    }

    // --- Helper to get 7-Day History Chart Data ---
    fun get7DayHistory(): List<DailyHistoryPoint> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormatter = SimpleDateFormat("E", Locale.getDefault())

        val list = allLogs.value
        val historyList = mutableListOf<DailyHistoryPoint>()

        // Generate past 7 days
        for (i in 6 downTo 0) {
            val testCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dateStr = formatter.format(testCal.time)
            val dayName = displayFormatter.format(testCal.time)

            val daysLogs = list.filter { it.dateString == dateStr }
            val totalCal = daysLogs.sumOf { it.calories }
            val totalProtein = daysLogs.sumOf { it.proteinG }

            historyList.add(DailyHistoryPoint(dayName, totalCal, totalProtein))
        }
        return historyList
    }

    data class DailyHistoryPoint(val dayLabel: String, val calories: Int, val protein: Int)
}
