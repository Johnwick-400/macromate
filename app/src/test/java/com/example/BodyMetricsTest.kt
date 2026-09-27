package com.example

import com.example.domain.BmiCategory
import com.example.domain.BodyMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMetricsTest {

    @Test
    fun `bmi is weight over height squared`() {
        assertEquals(22.77, BodyMetrics.bmi(62.0, 165.0)!!, 0.01)   // 62 / 1.65²
        assertEquals(22.86, BodyMetrics.bmi(70.0, 175.0)!!, 0.01)
    }

    @Test
    fun `WHO category boundaries`() {
        assertEquals(BmiCategory.UNDERWEIGHT, BodyMetrics.bmiCategory(18.49))
        assertEquals(BmiCategory.HEALTHY, BodyMetrics.bmiCategory(18.5))
        assertEquals(BmiCategory.HEALTHY, BodyMetrics.bmiCategory(24.99))
        assertEquals(BmiCategory.OVERWEIGHT, BodyMetrics.bmiCategory(25.0))
        assertEquals(BmiCategory.OBESE, BodyMetrics.bmiCategory(30.0))
    }

    @Test
    fun `mifflin st jeor matches the published equation`() {
        // 10·70 + 6.25·175 − 5·25 = 1668.75 → +5 male = 1673.75, −161 female = 1507.75
        assertEquals(1674, BodyMetrics.bmrMifflin(70.0, 175.0, 25, male = true))
        assertEquals(1508, BodyMetrics.bmrMifflin(70.0, 175.0, 25, male = false))
        assertEquals(1508..1674, BodyMetrics.bmrRange(70.0, 175.0, 25))
    }

    @Test
    fun `incomplete profile shows nothing rather than a made-up number`() {
        assertNull(BodyMetrics.bmi(0.0, 175.0))          // onboarding sentinel weight
        assertNull(BodyMetrics.bmrRange(0.0, 175.0, 25))
    }

    @Test
    fun `adult categories and healthy range`() {
        assertFalse(BodyMetrics.bmiCategoryApplies(16))
        assertTrue(BodyMetrics.bmiCategoryApplies(18))
        assertEquals(50 to 68, BodyMetrics.healthyWeightRangeKg(165.0)) // 18.5·1.65² = 50.4, 24.9·1.65² = 67.8
    }

    @Test
    fun `gender gives one exact BMR, unset keeps the range`() {
        assertEquals(1674, BodyMetrics.bmrFor(70.0, 175.0, 25, "Male"))
        assertEquals(1508, BodyMetrics.bmrFor(70.0, 175.0, 25, "Female"))
        assertNull(BodyMetrics.bmrFor(70.0, 175.0, 25, ""))
    }
}
