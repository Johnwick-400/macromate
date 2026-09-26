package com.example

import com.example.data.repository.SwiggyMcpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric needed for real org.json on the JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwiggyMcpParsingTest {

    @Test
    fun `parseRpcBody extracts json-rpc response from SSE stream`() {
        val sse = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"{}"}]}}

        """.trimIndent()
        val parsed = SwiggyMcpClient.parseRpcBody(sse, "text/event-stream")
        assertNotNull(parsed)
        assertEquals(1, parsed!!.optInt("id"))
        assertNotNull(parsed.optJSONObject("result"))
    }

    @Test
    fun `parseRpcBody handles plain json`() {
        val parsed = SwiggyMcpClient.parseRpcBody("""{"jsonrpc":"2.0","id":2,"result":{}}""", "application/json")
        assertEquals(2, parsed!!.optInt("id"))
    }

    @Test
    fun `parseRestaurants maps nested payload and skips closed restaurants`() {
        val data = JSONObject(
            """
            {"results":{"restaurants":[
                {"restaurantId":"r1","name":"Biryani House","avgRating":4.3,
                 "cuisines":["Biryani","North Indian"],"deliveryTimeMin":28,
                 "costForTwo":"₹400 for two","availabilityStatus":"OPEN"},
                {"restaurantId":"r2","name":"Closed Place","availabilityStatus":"CLOSED"}
            ]}}
            """
        )
        val restaurants = SwiggyMcpClient.parseRestaurants(data)
        assertEquals(1, restaurants.size)
        assertEquals("r1", restaurants[0].id)
        assertEquals(4.3, restaurants[0].rating, 0.001)
        assertEquals(200.0, restaurants[0].averagePrice, 0.001) // 400 for two / 2
        assertEquals(listOf("Biryani", "North Indian"), restaurants[0].cuisines)
    }

    @Test
    fun `parseMenuDishes collects dishes from nested categories and normalizes paise`() {
        val data = JSONObject(
            """
            {"menu":{"categories":[
                {"name":"Mains","items":[
                    {"itemId":"d1","name":"Chicken Biryani","price":29900,"description":"Classic dum biryani"},
                    {"itemId":"d2","name":"Dal Tadka","price":180.0}
                ]}
            ]}}
            """
        )
        val dishes = SwiggyMcpClient.parseMenuDishes(data, "r1")
        assertEquals(2, dishes.size)
        assertEquals(299.0, dishes.first { it.id == "d1" }.price, 0.001)
        assertEquals(180.0, dishes.first { it.id == "d2" }.price, 0.001)
        assertTrue(dishes.all { it.restaurantId == "r1" })
    }

    @Test
    fun `out of stock dishes and closed restaurants are filtered out`() {
        val data = JSONObject("""{"items":[
            {"id":"1","name":"In stock","price":100,"inStock":1},
            {"id":"2","name":"Sold out","price":100,"inStock":0},
            {"id":"3","name":"Sold out flag","price":100,"in_stock":false},
            {"id":"4","name":"Sold out string","price":100,"inStock":"0"},
            {"id":"5","name":"No flag","price":100}]}""")
        assertEquals(listOf("In stock", "No flag"), SwiggyMcpClient.parseMenuDishes(data, "r1").map { it.name })

        assertTrue(SwiggyMcpClient.isRestaurantOrderable(JSONObject("""{"availabilityStatus":"OPEN"}""")))
        assertTrue(!SwiggyMcpClient.isRestaurantOrderable(JSONObject("""{"availabilityStatus":"CLOSED"}""")))
        assertTrue(!SwiggyMcpClient.isRestaurantOrderable(JSONObject("""{"isOpen":false}""")))
    }
}
