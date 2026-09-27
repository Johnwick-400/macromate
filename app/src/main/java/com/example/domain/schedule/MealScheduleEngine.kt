package com.example.domain.schedule

import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Evidence-based constants used by [MealScheduleEngine]. All times are in minutes. */
object ScheduleRules {
    /** First meal ~1 h after waking (circadian meal-timing literature; design doc default). */
    const val FIRST_MEAL_AFTER_WAKE_MIN = 60

    /** Last main meal 2–3 h before bed (Fujiwara 2005, Am J Gastroenterol: late dinner vs reflux; sleep quality). */
    const val LAST_MEAL_BEFORE_SLEEP_MIN = 150

    /** Dinner later than this before sleep triggers the soft "2–3 h before bed" tip. */
    const val DINNER_TIP_THRESHOLD_MIN = 120

    /** Pre-sleep protein ~30–60 min before bed (Res 2012; Trommelen & van Loon 2016, Nutrients). */
    const val PRE_SLEEP_BEFORE_SLEEP_MIN = 45

    /** Pre-sleep casein dose 0.4 g/kg, 30–40 g (Snijders 2019, Front Nutr; ISSN nutrient timing 2017). */
    const val PRE_SLEEP_PROTEIN_G_PER_KG = 0.4
    const val PRE_SLEEP_PROTEIN_MIN_G = 30
    const val PRE_SLEEP_PROTEIN_MAX_G = 40

    /** Protein feedings every ~3 h outperform pulse/grazing patterns (Areta 2013, J Physiol). */
    const val MIN_COMFORTABLE_GAP_MIN = 150

    /** Floor used only when wake/sleep leave no room for even spacing. */
    const val MIN_HARD_GAP_MIN = 60

    /** Pre-exercise meal 1–4 h before training (ISSN position stand: nutrient timing, Kerksick 2017). */
    const val PRE_WORKOUT_WINDOW_MIN = 60
    const val PRE_WORKOUT_WINDOW_MAX = 240

    /** Post-exercise protein within ~2 h of the session (Kerksick 2017; Aragon & Schoenfeld 2013). */
    const val POST_WORKOUT_WINDOW_MAX = 120

    /** Assumed session length when only a start time is known (design doc). */
    const val ASSUMED_SESSION_MIN = 60

    /** Inserted slots (design doc). */
    const val POST_WORKOUT_INSERT_AFTER_END_MIN = 30
    const val PRE_WORKOUT_INSERT_BEFORE_MIN = 90
    const val PRE_WORKOUT_INSERT_MIN_GAP_MIN = 120

    /** Training-day periodisation: +5 % of daily kcal onto each workout meal (design doc; Kerksick 2017). */
    const val TRAINING_KCAL_SHIFT = 0.05

    /** Carb-leaning pre-workout meal gets +20 % carbs (design doc; ISSN carbohydrate guidance). */
    const val CARB_LEANING_BOOST = 0.20

    /** Per-meal protein 0.4 g/kg over ≥4 meals; ~0.25 g/kg minimum effective dose (Schoenfeld & Aragon 2018, JISSN). */
    const val PROTEIN_PER_MEAL_MIN_G_PER_KG = 0.25
    const val PROTEIN_PER_MEAL_MAX_G_PER_KG = 0.55

    /** Minimum daily protein feedings; ≥4 for hypertrophy (Schoenfeld & Aragon 2018). */
    const val MIN_PROTEIN_FEEDINGS = 3
    const val MIN_PROTEIN_FEEDINGS_BULK = 4

    /** Slot status window around the planned time (app UX choice). */
    const val DUE_BEFORE_MIN = 30
    const val DUE_AFTER_MIN = 90

    /** Remaining slots may absorb at most 1.5× their plan when catching up (design doc; avoids binge meals). */
    const val REBALANCE_CAP = 1.5

    /** Relative calorie weights for the optional small slots (design doc). */
    const val SMALL_SLOT_WEIGHT = 8.0

    /** Tag on a row for a meal logged to a section that isn't in the day's plan. */
    const val EXTRA_TAG = "extra"
}

object MealScheduleEngine {

    private const val DAY_MIN = 24 * 60
    private const val TAG_PRE = "pre-workout"
    private const val TAG_POST = "post-workout"
    private const val TAG_CARB = "carb-leaning"

    private class Draft(
        val type: MealSlotType,
        val minute: Int,             // on the day's timeline; may exceed 1440 when sleep is after midnight
        val isMain: Boolean,
        val tags: MutableSet<String> = mutableSetOf()
    )

    private class Timeline(wake: LocalTime, sleep: LocalTime) {
        val wakeMin = minutes(wake)
        val sleepMin = minutes(sleep).let { if (it <= wakeMin) it + DAY_MIN else it }

        /** Times shortly after midnight belong to the same "day" when sleep is after midnight. */
        fun normalize(t: LocalTime): Int {
            val m = minutes(t)
            return if (m < wakeMin && m + DAY_MIN <= sleepMin + 180) m + DAY_MIN else m
        }
    }

    fun buildDay(input: ScheduleInput, logged: List<LoggedMeal>, now: LocalTime?): DayPlan {
        val timeline = Timeline(input.wakeTime, input.sleepTime)
        val tips = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1–3. Slots, times and workout anchoring.
        val drafts = placeMainMeals(input, timeline, tips).toMutableList()
        if (input.preSleepProtein) {
            drafts += Draft(MealSlotType.PRE_SLEEP, timeline.sleepMin - ScheduleRules.PRE_SLEEP_BEFORE_SLEEP_MIN, isMain = false)
        }
        input.workoutTime?.let { anchorWorkout(drafts, timeline.normalize(it)) }
        drafts.sortBy { it.minute }

        // 4. Calories, carbs, fat.
        val kcalShares = kcalShares(drafts, input)
        val kcal = apportion(input.targetKcal, kcalShares)
        val fat = apportion(input.targetFatG, kcalShares)
        val carbs = apportion(input.targetCarbsG, carbShares(drafts, kcalShares))

        // 5. Protein.
        val protein = proteinSplit(drafts, input)
        warnings += proteinWarnings(drafts, protein, input)

        // 6. Logged meals → slots. Meals logged to a section the plan doesn't have become
        //    their own "extra" rows (no target) instead of silently merging into another meal.
        val (assigned, extras) = assignLogs(drafts, logged, timeline)

        // 7. Status.
        val nowMin = now?.let { timeline.normalize(it) }
        val statuses = drafts.indices.map { i ->
            val loggedKcal = assigned[i].sumOf { it.kcal }
            val t = drafts[i].minute
            when {
                loggedKcal >= 1 -> SlotStatus.LOGGED
                nowMin == null -> SlotStatus.UPCOMING
                nowMin in (t - ScheduleRules.DUE_BEFORE_MIN)..(t + ScheduleRules.DUE_AFTER_MIN) -> SlotStatus.DUE_NOW
                nowMin > t + ScheduleRules.DUE_AFTER_MIN -> SlotStatus.MISSED
                else -> SlotStatus.UPCOMING
            }
        }

        // 8. Rebalance what's still owed across open slots.
        var finalKcal = kcal
        var finalProtein = protein
        var finalCarbs = carbs
        var finalFat = fat
        if (nowMin != null) {
            val open = statuses.indices.filter { statuses[it] == SlotStatus.UPCOMING || statuses[it] == SlotStatus.DUE_NOW }
            if (open.isNotEmpty()) {
                finalKcal = rebalance(kcal, open, input.targetKcal - logged.sumOf { it.kcal })
                finalProtein = rebalance(protein, open, input.targetProteinG - logged.sumOf { it.proteinG })
                finalCarbs = rebalance(carbs, open, input.targetCarbsG - logged.sumOf { it.carbsG })
                finalFat = rebalance(fat, open, input.targetFatG - logged.sumOf { it.fatG })
            }
        }

        // 10. Soft tips.
        drafts.lastOrNull { it.type == MealSlotType.DINNER }?.let { dinner ->
            if (dinner.minute > timeline.sleepMin - ScheduleRules.DINNER_TIP_THRESHOLD_MIN) {
                tips += "Finishing dinner 2–3 h before bed can help sleep and hunger — optional."
            }
        }
        if (isGoal(input, "Cut") && input.template == ScheduleTemplate.BALANCED) {
            tips += "A bigger breakfast can reduce hunger through the day (same fat loss) — try the Front-loaded template."
        }
        tips += "Aim for about 8 glasses (2 L) of water, more on training days."

        val slots = drafts.mapIndexed { i, d ->
            MealSlot(
                type = d.type,
                time = toLocalTime(d.minute),
                targetKcal = finalKcal[i],
                targetProteinG = finalProtein[i],
                targetCarbsG = finalCarbs[i],
                targetFatG = finalFat[i],
                tags = d.tags.toSet(),
                loggedKcal = assigned[i].sumOf { it.kcal },
                loggedProteinG = assigned[i].sumOf { it.proteinG },
                status = statuses[i],
                loggedIds = assigned[i].map { it.id },
                suggestion = suggestion(d.type, input)
            )
        }
        val extraSlots = extras.map { (type, meals) ->
            MealSlot(
                type = type,
                time = meals.minOf { it.time },
                targetKcal = 0, targetProteinG = 0, targetCarbsG = 0, targetFatG = 0,
                tags = setOf(ScheduleRules.EXTRA_TAG),
                loggedKcal = meals.sumOf { it.kcal },
                loggedProteinG = meals.sumOf { it.proteinG },
                status = SlotStatus.LOGGED,
                loggedIds = meals.map { it.id },
                suggestion = ""
            )
        }
        val ordered = (slots + extraSlots).sortedBy { timeline.normalize(it.time) }
        return DayPlan(ordered, warnings, tips)
    }

    // ---- Rules 1–2: slot layout and timing ----

    private fun mainTypes(mealsPerDay: Int): List<MealSlotType> {
        val b = MealSlotType.BREAKFAST
        val l = MealSlotType.LUNCH
        val s = MealSlotType.SNACK
        val d = MealSlotType.DINNER
        return when (mealsPerDay.coerceIn(2, 6)) {
            2 -> listOf(b, d)
            3 -> listOf(b, l, d)
            4 -> listOf(b, l, s, d)
            5 -> listOf(b, s, l, s, d)
            else -> listOf(b, s, l, s, d, s)
        }
    }

    private fun placeMainMeals(input: ScheduleInput, timeline: Timeline, tips: MutableList<String>): List<Draft> {
        val types = mainTypes(input.mealsPerDay)
        val gaps = types.size - 1
        val first = timeline.wakeMin + ScheduleRules.FIRST_MEAL_AFTER_WAKE_MIN
        // The last main meal (dinner, or the late snack for 6 meals) lands 2–3 h before bed.
        val last = maxOf(
            timeline.sleepMin - ScheduleRules.LAST_MEAL_BEFORE_SLEEP_MIN,
            first + gaps * ScheduleRules.MIN_HARD_GAP_MIN
        )
        val spacing = (last - first).toDouble() / gaps
        if (spacing < ScheduleRules.MIN_COMFORTABLE_GAP_MIN) {
            tips += "Your meals are under 2.5 h apart — that's fine; fewer, larger meals are also an option."
        }
        return types.mapIndexed { i, type ->
            Draft(type, (first + spacing * i).roundToInt(), isMain = true)
        }
    }

    // ---- Rule 3: workout anchoring ----

    private fun anchorWorkout(drafts: MutableList<Draft>, workout: Int) {
        val mains = drafts.filter { it.isMain }.sortedBy { it.minute }
        val end = workout + ScheduleRules.ASSUMED_SESSION_MIN

        val pre = mains.lastOrNull {
            workout - it.minute in ScheduleRules.PRE_WORKOUT_WINDOW_MIN..ScheduleRules.PRE_WORKOUT_WINDOW_MAX
        }
        if (pre != null) {
            pre.tags += TAG_PRE
            pre.tags += TAG_CARB
        } else {
            val at = workout - ScheduleRules.PRE_WORKOUT_INSERT_BEFORE_MIN
            val previous = mains.lastOrNull { it.minute <= at }
            if (previous == null || at - previous.minute >= ScheduleRules.PRE_WORKOUT_INSERT_MIN_GAP_MIN) {
                drafts += Draft(MealSlotType.PRE_WORKOUT, at, isMain = false, tags = mutableSetOf(TAG_PRE, TAG_CARB))
            }
        }

        val post = mains.firstOrNull { it.minute - end in 0..ScheduleRules.POST_WORKOUT_WINDOW_MAX }
        if (post != null) {
            post.tags += TAG_POST
        } else {
            drafts += Draft(
                MealSlotType.POST_WORKOUT,
                end + ScheduleRules.POST_WORKOUT_INSERT_AFTER_END_MIN,
                isMain = false,
                tags = mutableSetOf(TAG_POST)
            )
        }
    }

    // ---- Rule 4: calories and carbs ----

    private fun baseWeight(type: MealSlotType, template: ScheduleTemplate, hasLunch: Boolean): Double {
        val front = template == ScheduleTemplate.FRONT_LOADED
        return when (type) {
            MealSlotType.BREAKFAST -> if (!hasLunch) (if (front) 55.0 else 45.0) else if (front) 35.0 else 25.0
            MealSlotType.DINNER -> if (!hasLunch) (if (front) 45.0 else 55.0) else if (front) 25.0 else 30.0
            MealSlotType.LUNCH -> if (front) 30.0 else 35.0
            MealSlotType.SNACK -> 10.0
            MealSlotType.PRE_WORKOUT, MealSlotType.POST_WORKOUT, MealSlotType.PRE_SLEEP -> ScheduleRules.SMALL_SLOT_WEIGHT
        }
    }

    /** Fraction of daily kcal per slot (sums to 1), including the training-day shift. */
    private fun kcalShares(drafts: List<Draft>, input: ScheduleInput): List<Double> {
        val hasLunch = drafts.any { it.type == MealSlotType.LUNCH }
        val weights = drafts.map { baseWeight(it.type, input.template, hasLunch) }
        val total = weights.sum()
        val shares = weights.map { it / total }.toMutableList()

        if (input.workoutTime != null) {
            val tagged = drafts.indices.filter { TAG_PRE in drafts[it].tags || TAG_POST in drafts[it].tags }
            val untagged = drafts.indices - tagged.toSet()
            val bonus = ScheduleRules.TRAINING_KCAL_SHIFT * tagged.size
            val untaggedTotal = untagged.sumOf { shares[it] }
            if (tagged.isNotEmpty() && untaggedTotal > bonus) {
                tagged.forEach { shares[it] += ScheduleRules.TRAINING_KCAL_SHIFT }
                val keep = 1 - bonus / untaggedTotal
                untagged.forEach { shares[it] *= keep }
            }
        }
        return shares
    }

    private fun carbShares(drafts: List<Draft>, kcalShares: List<Double>): List<Double> {
        val leaning = drafts.indices.filter { TAG_CARB in drafts[it].tags }
        val others = drafts.indices - leaning.toSet()
        val extra = leaning.sumOf { kcalShares[it] * ScheduleRules.CARB_LEANING_BOOST }
        val othersTotal = others.sumOf { kcalShares[it] }
        if (leaning.isEmpty() || othersTotal <= extra) return kcalShares
        return drafts.indices.map { i ->
            if (i in leaning) kcalShares[i] * (1 + ScheduleRules.CARB_LEANING_BOOST)
            else kcalShares[i] * (1 - extra / othersTotal)
        }
    }

    // ---- Rule 5: protein ----

    private fun proteinSplit(drafts: List<Draft>, input: ScheduleInput): List<Int> {
        val target = input.targetProteinG
        val preSleepIndex = drafts.indexOfFirst { it.type == MealSlotType.PRE_SLEEP }
        val preSleepG = if (preSleepIndex >= 0) {
            (ScheduleRules.PRE_SLEEP_PROTEIN_G_PER_KG * input.weightKg).roundToInt()
                .coerceIn(ScheduleRules.PRE_SLEEP_PROTEIN_MIN_G, ScheduleRules.PRE_SLEEP_PROTEIN_MAX_G)
                .coerceAtMost(target)
        } else 0

        val weights = drafts.mapIndexed { i, d ->
            when {
                i == preSleepIndex -> 0.0
                d.type == MealSlotType.SNACK -> 0.5
                else -> 1.0
            }
        }
        val rest = apportion(target - preSleepG, weights).toMutableList()
        if (preSleepIndex >= 0) rest[preSleepIndex] = preSleepG
        return rest
    }

    private fun proteinWarnings(drafts: List<Draft>, protein: List<Int>, input: ScheduleInput): List<String> {
        val kg = input.weightKg
        if (kg <= 0) return emptyList()
        val minG = ScheduleRules.PROTEIN_PER_MEAL_MIN_G_PER_KG * kg
        val maxG = ScheduleRules.PROTEIN_PER_MEAL_MAX_G_PER_KG * kg
        val out = mutableListOf<String>()

        // Snacks are small by design, so the per-meal band is checked on the main meals proper.
        drafts.forEachIndexed { i, d ->
            if (!d.isMain || d.type == MealSlotType.SNACK) return@forEachIndexed
            val p = protein[i]
            val label = d.type.label
            if (p < minG) {
                out += "$label protein $p g is below 0.25 g/kg (${minG.roundToInt()} g). " +
                    "Adding a meal or a protein-rich snack helps spread protein."
            } else if (p > maxG) {
                out += "$label protein $p g is above 0.55 g/kg (${maxG.roundToInt()} g). " +
                    "That's fine — spreading it over more meals may use it a little better."
            }
        }

        val feedings = protein.count { it >= minG }
        val needed = if (isGoal(input, "Bulk")) ScheduleRules.MIN_PROTEIN_FEEDINGS_BULK else ScheduleRules.MIN_PROTEIN_FEEDINGS
        if (feedings < needed) {
            out += "Only $feedings meal${if (feedings == 1) "" else "s"} reach ${minG.roundToInt()} g protein (0.25 g/kg). " +
                "Aim for at least $needed protein feedings a day."
        }
        return out
    }

    // ---- Rule 6: logged meals ----

    /**
     * Meals with an explicit section go to that section; a section missing from the plan
     * becomes an "extra" row. Untagged meals (older logs) fall back to the nearest slot by time.
     */
    private fun assignLogs(
        drafts: List<Draft>,
        logged: List<LoggedMeal>,
        timeline: Timeline
    ): Pair<List<List<LoggedMeal>>, Map<MealSlotType, List<LoggedMeal>>> {
        val buckets = List(drafts.size) { mutableListOf<LoggedMeal>() }
        val extras = linkedMapOf<MealSlotType, MutableList<LoggedMeal>>()
        for (meal in logged) {
            val t = timeline.normalize(meal.time)
            val nearest = { candidates: List<Int> -> candidates.minBy { abs(drafts[it].minute - t) } }
            val explicit = meal.slot
            if (explicit != null) {
                val sameType = drafts.indices.filter { drafts[it].type == explicit }
                if (sameType.isEmpty()) {
                    extras.getOrPut(explicit) { mutableListOf() } += meal
                } else {
                    // Several slots of one type (e.g. two snacks): the one closest in time.
                    buckets[nearest(sameType)] += meal
                }
            } else if (drafts.isNotEmpty()) {
                buckets[nearest(drafts.indices.toList())] += meal
            }
        }
        return buckets to extras
    }

    // ---- Rule 8: rebalancing ----

    /**
     * Spreads [owed] over the [open] slots in proportion to their plan, each capped at
     * [ScheduleRules.REBALANCE_CAP]× plan (water-filling). Closed slots keep their planned numbers.
     */
    private fun rebalance(planned: List<Int>, open: List<Int>, owed: Int): List<Int> {
        val result = planned.toMutableList()
        val caps = open.associateWith { floor(planned[it] * ScheduleRules.REBALANCE_CAP).toInt() }
        val budget = owed.coerceAtLeast(0).coerceAtMost(caps.values.sum())
        val alloc = open.associateWith { 0.0 }.toMutableMap()

        var active = open.filter { planned[it] > 0 }
        var remaining = budget.toDouble()
        while (active.isNotEmpty() && remaining > 1e-9) {
            val weight = active.sumOf { planned[it] }.toDouble()
            val capped = active.filter { remaining * planned[it] / weight >= caps.getValue(it) }
            if (capped.isEmpty()) {
                active.forEach { alloc[it] = remaining * planned[it] / weight }
                break
            }
            capped.forEach {
                alloc[it] = caps.getValue(it).toDouble()
                remaining -= caps.getValue(it)
            }
            active = active - capped.toSet()
        }

        // Integerise with largest remainders, never exceeding a cap.
        val ints = alloc.mapValues { floor(it.value).toInt() }.toMutableMap()
        var leftover = budget - ints.values.sum()
        val byRemainder = open.sortedByDescending { alloc.getValue(it) - ints.getValue(it) }
        while (leftover > 0) {
            val before = leftover
            for (i in byRemainder) {
                if (leftover == 0) break
                if (ints.getValue(i) < caps.getValue(i)) {
                    ints[i] = ints.getValue(i) + 1
                    leftover--
                }
            }
            if (leftover == before) break
        }
        open.forEach { result[it] = ints.getValue(it).coerceAtLeast(0) }
        return result
    }

    // ---- Rule 9: suggestions ----

    private fun suggestion(type: MealSlotType, input: ScheduleInput): String {
        val veg = input.vegetarian
        val base = when (type) {
            MealSlotType.BREAKFAST -> if (veg) "Poha with peanuts + curd" else "Egg bhurji with 2 rotis"
            MealSlotType.LUNCH -> if (veg) "Rice, dal, sabzi, salad" else "Rice, dal, chicken curry, salad"
            MealSlotType.SNACK -> "Roasted chana or fruit with buttermilk"
            MealSlotType.DINNER -> if (veg) "Rotis, rajma or paneer, salad" else "Rotis, fish or chicken, salad"
            MealSlotType.PRE_WORKOUT -> "Banana with peanut chikki"
            MealSlotType.POST_WORKOUT -> if (veg) "Curd or milk with fruit" else "Eggs or whey with fruit"
            MealSlotType.PRE_SLEEP -> "Paneer or curd with milk"
        }
        val addVeg = isGoal(input, "Cut") && (type == MealSlotType.LUNCH || type == MealSlotType.DINNER)
        return if (addVeg) "$base (add extra vegetables)" else base
    }

    // ---- helpers ----

    /** Splits [total] by [weights] into integers that sum exactly to [total]; rounding drift goes to the largest slot. */
    private fun apportion(total: Int, weights: List<Double>): List<Int> {
        if (weights.isEmpty()) return emptyList()
        val sum = weights.sum()
        if (sum <= 0) return weights.indices.map { if (it == 0) total else 0 }
        val raw = weights.map { total * it / sum }
        val rounded = raw.map { it.roundToInt() }.toMutableList()
        val largest = raw.indices.maxBy { raw[it] }
        rounded[largest] += total - rounded.sum()
        return rounded
    }

    private fun isGoal(input: ScheduleInput, goal: String) = input.goal.equals(goal, ignoreCase = true)

    private fun minutes(t: LocalTime) = t.hour * 60 + t.minute

    private fun toLocalTime(minute: Int): LocalTime =
        LocalTime.MIDNIGHT.plusMinutes(Math.floorMod(minute, DAY_MIN).toLong())
}
