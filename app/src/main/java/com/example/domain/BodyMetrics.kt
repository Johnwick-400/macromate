package com.example.domain

import kotlin.math.roundToInt

enum class BmiCategory(val label: String) {
    UNDERWEIGHT("Underweight"),
    HEALTHY("Healthy weight"),
    OVERWEIGHT("Overweight"),
    OBESE("Obese")
}

/**
 * BMI with WHO adult categories (<18.5 / 18.5–24.9 / 25–29.9 / ≥30; WHO Expert Consultation,
 * Lancet 2004) and BMR by Mifflin-St Jeor, the most reliable resting-energy equation in
 * Frankenfield et al. 2005 (J Am Diet Assoc). Both are estimates for adults.
 */
object BodyMetrics {

    /** kg / m², or null when height/weight aren't set yet. */
    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 10.0 || heightCm <= 50.0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.UNDERWEIGHT
        bmi < 25.0 -> BmiCategory.HEALTHY
        bmi < 30.0 -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }

    /** Adult BMI categories don't apply under 18 (children need BMI-for-age charts). */
    fun bmiCategoryApplies(age: Int): Boolean = age >= 18

    /** Weight range for a healthy BMI (18.5–24.9) at this height. */
    fun healthyWeightRangeKg(heightCm: Double): Pair<Int, Int>? {
        if (heightCm <= 50.0) return null
        val m2 = (heightCm / 100.0).let { it * it }
        return (18.5 * m2).roundToInt() to (24.9 * m2).roundToInt()
    }

    /** Mifflin-St Jeor: 10·kg + 6.25·cm − 5·age + s, where s = +5 (male) or −161 (female). */
    fun bmrMifflin(weightKg: Double, heightCm: Double, age: Int, male: Boolean): Int =
        (10 * weightKg + 6.25 * heightCm - 5 * age + if (male) 5 else -161).roundToInt()

    /** Exact BMR for "Male"/"Female"; null when sex isn't set or the profile is incomplete. */
    fun bmrFor(weightKg: Double, heightCm: Double, age: Int, sex: String): Int? {
        if (weightKg <= 10.0 || heightCm <= 50.0 || age <= 0) return null
        return when (sex) {
            "Male" -> bmrMifflin(weightKg, heightCm, age, male = true)
            "Female" -> bmrMifflin(weightKg, heightCm, age, male = false)
            else -> null
        }
    }

    /**
     * BMR when sex isn't recorded: the female and male equations bound the true value.
     * Null when the profile isn't complete.
     */
    fun bmrRange(weightKg: Double, heightCm: Double, age: Int): IntRange? {
        if (weightKg <= 10.0 || heightCm <= 50.0 || age <= 0) return null
        return bmrMifflin(weightKg, heightCm, age, male = false)..bmrMifflin(weightKg, heightCm, age, male = true)
    }
}
