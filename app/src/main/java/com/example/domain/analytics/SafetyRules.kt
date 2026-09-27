package com.example.domain.analytics

/**
 * Who shouldn't bank calories across days or be nudged toward fasting / deeper deficits
 * without a clinician: pregnancy or breastfeeding, diabetes on insulin or sulfonylureas
 * (IDF-DAR 2021), a history of eating disorders (Academy of Nutrition and Dietetics),
 * under-18s, and underweight adults (WHO BMI < 18.5).
 */
object SafetyRules {
    fun reasons(
        age: Int,
        weightKg: Double,
        heightCm: Double,
        pregnantOrBreastfeeding: Boolean,
        onDiabetesMedication: Boolean,
        eatingDisorderHistory: Boolean
    ): List<String> = buildList {
        if (pregnantOrBreastfeeding) add("pregnancy or breastfeeding")
        if (onDiabetesMedication) add("diabetes medication")
        if (eatingDisorderHistory) add("eating-disorder history")
        if (age in 1..17) add("age under 18")
        val bmi = if (heightCm > 0) weightKg / ((heightCm / 100) * (heightCm / 100)) else null
        if (bmi != null && weightKg > 10 && bmi < 18.5) add("BMI under 18.5")
    }
}
