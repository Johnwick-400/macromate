package com.example

import com.example.data.repository.extractJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QwenJsonTest {
    @Test
    fun extractsJsonFromFencesAndSurroundingProse() {
        val fenced = "Sure!\n```json\n{\"message\": \"Great picks\", \"reasonings\": [\"a\", \"b\"]}\n```\nHope that helps."
        val json = extractJsonObject(fenced)
        assertEquals("Great picks", json?.getString("message"))
        assertEquals(2, json?.getJSONArray("reasonings")?.length())
    }

    @Test
    fun returnsNullWhenThereIsNoUsableJson() {
        assertNull(extractJsonObject("no braces here"))
        assertNull(extractJsonObject("{\"broken\": "))
        assertNull(extractJsonObject("} reversed {"))
    }
}
