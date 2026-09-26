package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.data.model.DishItem
import com.example.data.model.Restaurant
import com.example.data.model.SwiggyAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client for the real Swiggy MCP Food server (https://mcp.swiggy.com/builders/docs/).
 *
 * - Auth: OAuth 2.1 + PKCE with dynamic client registration (RFC 7591).
 *   Redirect is caught on a localhost loopback socket (RFC 8252 native-app pattern),
 *   since Swiggy only allowlists https/localhost redirect URIs.
 * - Transport: MCP streamable HTTP (JSON-RPC 2.0 POST, responses may be SSE).
 * - Tools used: get_addresses, search_restaurants, get_restaurant_menu.
 *
 * No refresh tokens in Swiggy v1.0 — when the access token expires (~5 days),
 * the user must reconnect from the Profile screen.
 */
data class SwiggyAddressPage(val addresses: List<SwiggyAddress>, val hasMore: Boolean)

object SwiggyMcpClient {
    private const val TAG = "SwiggyMcpClient"

    private const val BASE = "https://mcp.swiggy.com"
    private const val FOOD_URL = "$BASE/food"
    private const val REGISTER_URL = "$BASE/auth/register"
    private const val AUTHORIZE_URL = "$BASE/auth/authorize"
    private const val TOKEN_URL = "$BASE/auth/token"

    private const val REDIRECT_PORT = 8971
    private const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT/callback"
    private const val SCOPE = "mcp:tools"
    private const val PROTOCOL_VERSION = "2025-06-18"

    private val jsonMediaType = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private lateinit var prefs: SharedPreferences
    private var mcpSessionId: String? = null
    private val rpcId = AtomicInteger(1)

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("swiggy_mcp", Context.MODE_PRIVATE)
    }

    private val accessToken: String? get() = prefs.getString("access_token", null)
    private val addressId: String? get() = prefs.getString("address_id", null)

    fun isConnected(): Boolean {
        if (!::prefs.isInitialized) return false
        return accessToken != null &&
                addressId != null &&
                System.currentTimeMillis() < prefs.getLong("expires_at", 0L)
    }

    /** Human-readable label for whichever saved address is currently active, e.g. "Home — 12 MG Road...". */
    fun getActiveAddressDisplay(): String? = prefs.getString("address_display", null)

    /** Switches which saved Swiggy address subsequent search/menu calls resolve against. */
    fun setActiveAddress(address: SwiggyAddress) {
        prefs.edit()
            .putString("address_id", address.id)
            .putString("address_display", displayFor(address))
            .apply()
    }

    private fun displayFor(address: SwiggyAddress): String =
        if (address.line.isNotBlank()) "${address.label} — ${address.line}" else address.label

    fun disconnect() {
        prefs.edit().remove("access_token").remove("expires_at").remove("address_id").remove("address_display").apply()
        mcpSessionId = null
    }

    // ---------------------------------------------------------------
    // OAuth 2.1 + PKCE
    // ---------------------------------------------------------------

    /**
     * Full connect flow. [openBrowser] is called with the authorization URL;
     * the user completes Swiggy's phone+OTP login in the browser, which
     * redirects to localhost where we catch the code.
     */
    suspend fun connect(openBrowser: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = prefs.getString("client_id", null) ?: registerClient()

            val verifier = randomUrlSafe(64)
            val challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val state = randomUrlSafe(16)

            val authUrl = AUTHORIZE_URL +
                    "?response_type=code" +
                    "&client_id=$clientId" +
                    "&redirect_uri=$REDIRECT_URI" +
                    "&scope=${SCOPE.replace(":", "%3A")}" +
                    "&state=$state" +
                    "&code_challenge=$challenge" +
                    "&code_challenge_method=S256"

            val code = awaitAuthorizationCode(state) { openBrowser(authUrl) }
            exchangeToken(clientId, code, verifier)

            // A usable connection needs a delivery address for every Food tool call.
            // Default to the first saved address; the user can switch via the address picker in Profile.
            mcpSessionId = null
            val first = fetchAddresses(page = 1).addresses.firstOrNull()
                ?: throw IllegalStateException("No saved addresses on this Swiggy account. Add one in the Swiggy app, then reconnect.")
            setActiveAddress(first)
        }.onFailure { Log.e(TAG, "Swiggy connect failed", it) }
    }

    private fun registerClient(): String {
        val body = JSONObject()
            .put("client_name", "MacroMate")
            .put("redirect_uris", JSONArray().put(REDIRECT_URI))
            .put("grant_types", JSONArray().put("authorization_code"))
            .put("response_types", JSONArray().put("code"))
            .put("token_endpoint_auth_method", "none")
            .put("scope", SCOPE)

        val request = Request.Builder()
            .url(REGISTER_URL)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("Client registration failed (${resp.code}): $text")
            val clientId = JSONObject(text).getString("client_id")
            prefs.edit().putString("client_id", clientId).apply()
            return clientId
        }
    }

    /** Listens on localhost for the OAuth redirect and returns the auth code. */
    private fun awaitAuthorizationCode(expectedState: String, launchAuth: () -> Unit): String {
        ServerSocket(REDIRECT_PORT, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 300_000 // 5 min for phone+OTP login
            launchAuth()
            // Loop: browsers also request /favicon.ico etc.
            while (true) {
                server.accept().use { socket ->
                    val requestLine = socket.getInputStream().bufferedReader().readLine() ?: return@use
                    val path = requestLine.split(" ").getOrNull(1) ?: return@use
                    if (!path.startsWith("/callback")) return@use

                    val params = path.substringAfter('?', "").split("&").mapNotNull {
                        val kv = it.split("=", limit = 2)
                        if (kv.size == 2) kv[0] to URLDecoder.decode(kv[1], "UTF-8") else null
                    }.toMap()

                    socket.getOutputStream().write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n" +
                                "<html><body style='font-family:sans-serif'><h2>MacroMate is connected to Swiggy 🎉</h2>" +
                                "Close this tab and return to the app.</body></html>").toByteArray()
                    )
                    socket.getOutputStream().flush()

                    if (params["state"] != expectedState) throw IllegalStateException("OAuth state mismatch")
                    return params["code"]
                        ?: throw IllegalStateException(params["error_description"] ?: params["error"] ?: "Authorization denied")
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("unreachable")
    }

    private fun exchangeToken(clientId: String, code: String, verifier: String) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()

        http.newCall(Request.Builder().url(TOKEN_URL).post(form).build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("Token exchange failed (${resp.code}): $text")
            val json = JSONObject(text)
            val expiresIn = json.optLong("expires_in", TimeUnit.DAYS.toSeconds(5))
            prefs.edit()
                .putString("access_token", json.getString("access_token"))
                .putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000)
                .apply()
        }
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    // ---------------------------------------------------------------
    // MCP JSON-RPC over streamable HTTP
    // ---------------------------------------------------------------

    private fun mcpPost(payload: JSONObject): Pair<JSONObject?, okhttp3.Response> {
        val builder = Request.Builder()
            .url(FOOD_URL)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer ${accessToken ?: throw IllegalStateException("Not connected to Swiggy")}")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
        mcpSessionId?.let { builder.header("Mcp-Session-Id", it) }

        val resp = http.newCall(builder.build()).execute()
        val bodyText = resp.body?.string() ?: ""
        resp.close()

        if (resp.code == 401) {
            disconnect()
            throw IllegalStateException("Swiggy session expired — reconnect from Profile.")
        }
        if (!resp.isSuccessful && resp.code != 202) {
            throw IllegalStateException("Swiggy MCP HTTP ${resp.code}: ${bodyText.take(300)}")
        }

        val json = parseRpcBody(bodyText, resp.header("Content-Type") ?: "")
        json?.optJSONObject("error")?.let {
            throw IllegalStateException("Swiggy MCP error: ${it.optString("message")}")
        }
        return json to resp
    }

    /** SSE bodies carry the JSON-RPC response in `data:` lines; plain JSON otherwise. */
    internal fun parseRpcBody(body: String, contentType: String): JSONObject? {
        if (body.isBlank()) return null
        if (!contentType.contains("text/event-stream")) {
            return try { JSONObject(body) } catch (e: Exception) { null }
        }
        var last: JSONObject? = null
        for (line in body.lineSequence()) {
            if (!line.startsWith("data:")) continue
            try {
                val j = JSONObject(line.removePrefix("data:").trim())
                if (j.has("result") || j.has("error")) last = j
            } catch (_: Exception) { /* keep-alives, non-JSON events */ }
        }
        return last
    }

    private fun ensureSession() {
        if (mcpSessionId != null) return

        val init = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", rpcId.getAndIncrement())
            .put("method", "initialize")
            .put("params", JSONObject()
                .put("protocolVersion", PROTOCOL_VERSION)
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "MacroMate").put("version", "1.0")))
        val (_, resp) = mcpPost(init)
        mcpSessionId = resp.header("Mcp-Session-Id")

        // Required handshake completion notification (no id, fire-and-forget).
        try {
            mcpPost(JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized"))
        } catch (e: Exception) {
            Log.w(TAG, "initialized notification failed: ${e.message}")
        }
    }

    /** Calls an MCP tool and unwraps Swiggy's {success, data} envelope. */
    private fun callTool(name: String, arguments: JSONObject): JSONObject {
        ensureSession()
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", rpcId.getAndIncrement())
            .put("method", "tools/call")
            .put("params", JSONObject().put("name", name).put("arguments", arguments))

        val (json, _) = mcpPost(payload)
        val result = json?.optJSONObject("result")
            ?: throw IllegalStateException("Empty MCP response for $name")

        val envelope = result.optJSONObject("structuredContent")
            ?: result.optJSONArray("content")?.let { content ->
                (0 until content.length())
                    .mapNotNull { content.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }
                    .firstNotNullOfOrNull { runCatching { JSONObject(it) }.getOrNull() }
            }
            ?: throw IllegalStateException("Unparseable MCP result for $name")

        if (envelope.has("success") && !envelope.optBoolean("success")) {
            throw IllegalStateException(envelope.optJSONObject("error")?.optString("message") ?: "$name failed")
        }
        return envelope.optJSONObject("data") ?: envelope
    }

    // ---------------------------------------------------------------
    // High-level Food tools mapped to MacroMate models
    // ---------------------------------------------------------------

    /** Saved addresses, most-recently-ordered-from first (Swiggy's own sort order). */
    suspend fun fetchAddresses(page: Int = 1, pageSize: Int = 10): SwiggyAddressPage = withContext(Dispatchers.IO) {
        val data = callTool("get_addresses", JSONObject().put("page", page).put("pageSize", pageSize))
        val arr = findObjectArray(data, preferKeyContaining = "address") ?: JSONArray()
        val addresses = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = firstString(o, "id", "addressId") ?: return@mapNotNull null
            SwiggyAddress(
                id = id,
                label = firstString(o, "addressTag", "addressCategory")?.takeIf(String::isNotBlank) ?: "Saved address",
                line = firstString(o, "addressLine").orEmpty()
            )
        }
        val hasMore = data.optJSONObject("pagination")?.optBoolean("hasMore", false) ?: false
        SwiggyAddressPage(addresses, hasMore)
    }

    /**
     * Pushes items into the user's real Swiggy cart (single restaurant per call — Swiggy
     * carts, like every food-delivery cart, hold one restaurant at a time). Returns the
     * "to pay" total when Swiggy's response includes one, so the caller can show it.
     */
    suspend fun updateCart(restaurantId: String, restaurantName: String, items: List<Pair<String, Int>>): Double? =
        withContext(Dispatchers.IO) {
            val cartItems = JSONArray()
            items.forEach { (itemId, quantity) ->
                cartItems.put(JSONObject().put("itemId", itemId).put("quantity", quantity))
            }
            val data = callTool(
                "update_food_cart",
                JSONObject()
                    .put("restaurantId", restaurantId)
                    .put("restaurantName", restaurantName)
                    .put("cartItems", cartItems)
                    .put("addressId", addressId ?: throw IllegalStateException("Not connected to Swiggy"))
            )
            extractToPay(data)
        }

    private fun extractToPay(data: JSONObject): Double? {
        data.optDouble("to_pay").takeIf { !it.isNaN() }?.let { return it }
        for (key in listOf("pricingSummary", "pricing")) {
            data.optJSONObject(key)?.optDouble("to_pay")?.takeIf { !it.isNaN() }?.let { return it }
        }
        return null
    }

    suspend fun searchRestaurants(query: String): List<Restaurant> = withContext(Dispatchers.IO) {
        val data = callTool(
            "search_restaurants",
            JSONObject().put("addressId", addressId).put("query", query)
        )
        parseRestaurants(data)
    }

    suspend fun getMenu(restaurantId: String): List<DishItem> = withContext(Dispatchers.IO) {
        val data = callTool(
            "get_restaurant_menu",
            JSONObject().put("addressId", addressId).put("restaurantId", restaurantId)
        )
        val dishes = parseMenuDishes(data, restaurantId)
        if (dishes.isEmpty()) return@withContext dishes
        estimateMacrosWithGemini(dishes) ?: dishes.map { it.withHeuristicMacros() }
    }

    // ---------------------------------------------------------------
    // Defensive payload mapping (exact field names aren't pinned in docs)
    // ---------------------------------------------------------------

    internal fun parseRestaurants(data: JSONObject): List<Restaurant> {
        val arr = findObjectArray(data, preferKeyContaining = "restaurant") ?: return emptyList()
        val out = mutableListOf<Restaurant>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = firstString(o, "restaurantId", "id") ?: continue
            val name = firstString(o, "name", "restaurantName") ?: continue
            if (o.optString("availabilityStatus").equals("CLOSED", true)) continue

            val cuisines = o.optJSONArray("cuisines")?.let { c ->
                (0 until c.length()).mapNotNull { c.optString(it).takeIf(String::isNotBlank) }
            } ?: firstString(o, "cuisines", "cuisine")?.split(",")?.map(String::trim).orEmpty()

            out.add(
                Restaurant(
                    id = id,
                    name = name,
                    rating = o.optDouble("avgRating", o.optDouble("rating", 4.0)),
                    cuisines = cuisines,
                    deliveryTimeMin = o.optInt("deliveryTimeMin", o.optInt("sla", 30)),
                    averagePrice = parseAveragePrice(o)
                )
            )
        }
        return out
    }

    private fun parseAveragePrice(o: JSONObject): Double {
        firstString(o, "costForTwo", "costForTwoMessage")?.let { text ->
            val digits = text.filter(Char::isDigit)
            if (digits.isNotEmpty()) return normalizePrice(digits.toDouble()) / 2
        }
        return 250.0
    }

    internal fun parseMenuDishes(data: JSONObject, restaurantId: String): List<DishItem> {
        val found = mutableListOf<DishItem>()
        collectDishes(data, restaurantId, found, depth = 0)
        return found.distinctBy { it.id }
    }

    /** Recursively collects anything that looks like a dish (has name + price). */
    private fun collectDishes(node: Any?, restaurantId: String, out: MutableList<DishItem>, depth: Int) {
        if (depth > 6) return
        when (node) {
            is JSONObject -> {
                val name = node.optString("name")
                val price = node.optDouble("price", node.optDouble("defaultPrice", -1.0))
                if (name.isNotBlank() && price >= 0) {
                    out.add(
                        DishItem(
                            id = firstString(node, "itemId", "id", "dishId") ?: "$restaurantId-${name.hashCode()}",
                            name = name,
                            description = firstString(node, "description", "desc").orEmpty(),
                            price = normalizePrice(price),
                            restaurantId = restaurantId
                        )
                    )
                } else {
                    node.keys().forEach { collectDishes(node.opt(it), restaurantId, out, depth + 1) }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collectDishes(node.opt(i), restaurantId, out, depth + 1)
        }
    }

    /** Swiggy APIs often quote prices in paise. */
    internal fun normalizePrice(price: Double): Double = if (price >= 5000) price / 100 else price

    private fun firstString(o: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { o.optString(it).takeIf(String::isNotBlank) }

    /** BFS for the first array of JSONObjects, optionally preferring a key name. */
    private fun findObjectArray(root: JSONObject, preferKeyContaining: String? = null): JSONArray? {
        val queue = ArrayDeque<JSONObject>().apply { add(root) }
        var fallback: JSONArray? = null
        var depth = 0
        while (queue.isNotEmpty() && depth < 200) {
            depth++
            val obj = queue.removeFirst()
            for (key in obj.keys()) {
                when (val v = obj.opt(key)) {
                    is JSONArray -> if (v.length() > 0 && v.optJSONObject(0) != null) {
                        if (preferKeyContaining != null && key.lowercase(Locale.ROOT).contains(preferKeyContaining)) return v
                        if (fallback == null) fallback = v
                    }
                    is JSONObject -> queue.add(v)
                }
            }
        }
        return fallback
    }

    // ---------------------------------------------------------------
    // Macro estimation ("getMenuAndEstimateMacros")
    // ---------------------------------------------------------------

    /** One batched Gemini call per menu; null when no API key / parse failure. */
    private suspend fun estimateMacrosWithGemini(dishes: List<DishItem>): List<DishItem>? {
        if (AgentLlm.mode() != AgentLlm.Mode.CLOUD) return null // local Qwen is too slow for whole menus: use the heuristic
        val menu = dishes.mapIndexed { i, d -> "$i. ${d.name} — ${d.description.take(120)}" }.joinToString("\n")
        val json = RestGeminiClient.generateJson(
            prompt = "Estimate nutrition per single serving for each restaurant dish below. " +
                    "Return JSON: {\"items\":[{\"index\":0,\"calories\":320,\"proteinG\":25,\"carbsG\":30,\"fatG\":10}, ...]} " +
                    "with exactly one entry per dish, in order.\n$menu",
            systemInstruction = "You are a nutritionist. Respond with JSON only."
        ) ?: return null
        val items = json.optJSONArray("items") ?: return null

        return try {
            val byIndex = HashMap<Int, JSONObject>()
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let { byIndex[it.optInt("index", i)] = it }
            }
            dishes.mapIndexed { i, d ->
                val m = byIndex[i] ?: return@mapIndexed d.withHeuristicMacros()
                d.copy(
                    calories = m.optInt("calories", 300),
                    proteinG = m.optInt("proteinG", 10),
                    carbsG = m.optInt("carbsG", 30),
                    fatG = m.optInt("fatG", 10)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini macro estimation parse failed", e)
            null
        }
    }

    // Smart Indian Culinary Nutritional Heuristic Engine
    // Computes realistic, varied macros based on dish category, ingredients, and cooking style.
    private fun DishItem.withHeuristicMacros(): DishItem {
        val t = "$name $description".lowercase(Locale.ROOT)
        val nameHash = (name.hashCode().and(0x7fffffff) % 7) - 3 // Small pseudo-random offset (-3 to +3) to avoid duplicates

        var p = 12
        var c = 35
        var f = 10

        // 1. Core Dish Type Identification
        when {
            t.contains("biryani") || t.contains("pulao") || t.contains("pulav") -> {
                p = if (t.contains("chicken") || t.contains("mutton")) 32 else 18
                c = 72 + nameHash
                f = 19 + (nameHash / 2)
            }
            t.contains("paratha") || t.contains("naan") || t.contains("kulcha") -> {
                p = if (t.contains("paneer") || t.contains("egg")) 16 else 9
                c = 48 + nameHash
                f = 16 + (nameHash / 2)
            }
            t.contains("bhurji") || t.contains("omelette") || t.contains("egg") -> {
                p = 22 + nameHash
                c = if (t.contains("meal") || t.contains("roti") || t.contains("rice")) 38 else 8
                f = 15
            }
            t.contains("tikka") || t.contains("kebab") || t.contains("tandoori") || t.contains("grilled") -> {
                p = if (t.contains("chicken") || t.contains("fish") || t.contains("mutton")) 36 + nameHash else 24 + nameHash
                c = (12 + nameHash).coerceAtLeast(4)
                f = 11
            }
            t.contains("salad") || t.contains("sprouts") || t.contains("boiled") -> {
                p = if (t.contains("chicken") || t.contains("paneer") || t.contains("egg")) 28 + nameHash else 14
                c = 18 + nameHash
                f = 7
            }
            t.contains("khichdi") || t.contains("dal") || t.contains("sambar") || t.contains("curry") -> {
                p = if (t.contains("chicken") || t.contains("paneer")) 24 + nameHash else 14
                c = 46 + nameHash
                f = 9
            }
            t.contains("bowl") || t.contains("fit") -> {
                p = if (t.contains("chicken")) 34 + nameHash else 22 + nameHash
                c = 42 + nameHash
                f = 11
            }
            t.contains("roll") || t.contains("wrap") || t.contains("sandwich") || t.contains("burger") -> {
                p = if (t.contains("chicken") || t.contains("paneer")) 24 + nameHash else 12
                c = 52 + nameHash
                f = 17
            }
            t.contains("juice") || t.contains("water") || t.contains("chaas") || t.contains("buttermilk") || t.contains("soda") -> {
                p = if (t.contains("chaas") || t.contains("buttermilk") || t.contains("lassi")) 5 else 1
                c = 14 + nameHash
                f = if (t.contains("lassi")) 6 else 1
            }
            else -> {
                val isHighProtein = listOf("chicken", "mutton", "fish", "prawn", "egg", "paneer", "tofu", "soya").any(t::contains)
                val isHeavyCarb = listOf("rice", "noodle", "pasta", "roti", "dosa", "idli", "bread", "maggi").any(t::contains)
                p = if (isHighProtein) (26 + nameHash) else 10
                c = if (isHeavyCarb) (52 + nameHash) else 22
                f = if (t.contains("butter") || t.contains("ghee") || t.contains("fried") || t.contains("creamy")) 18 else 10
            }
        }

        // 2. Oil / Cooking Fat Adjustments
        if (t.contains("butter") || t.contains("creamy") || t.contains("crispy") || t.contains("fried")) {
            f += 5
        } else if (t.contains("diet") || t.contains("steamed") || t.contains("boiled") || t.contains("clear")) {
            f = (f - 4).coerceAtLeast(3)
        }

        val finalP = p.coerceAtLeast(2)
        val finalC = c.coerceAtLeast(4)
        val finalF = f.coerceAtLeast(2)
        val cal = (finalP * 4) + (finalC * 4) + (finalF * 9)

        return copy(proteinG = finalP, carbsG = finalC, fatG = finalF, calories = cal)
    }
}
