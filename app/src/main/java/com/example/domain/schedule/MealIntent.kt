package com.example.domain.schedule

import java.util.Locale

/**
 * Which Dietary Log section a chat request (typed or voice) is for. Deterministic word rules,
 * so the same sentence always lands in the same section. Returns null when no meal is named;
 * callers then use [nextSlot] — the user's next meal by their own schedule.
 */
object MealIntent {

    // Checked in order: specific phrases before single words ("evening snack" before "evening").
    private val RULES: List<Pair<Regex, MealSlotType>> = listOf(
        """\b(pre[- ]?workout|before (the )?(gym|workout|training|run))\b""" to MealSlotType.PRE_WORKOUT,
        """\b(post[- ]?workout|after (the )?(gym|workout|training|run)|recovery meal)\b""" to MealSlotType.POST_WORKOUT,
        """\b(before (bed|sleep|sleeping)|bed ?time|pre[- ]?sleep|midnight snack)\b""" to MealSlotType.PRE_SLEEP,
        """\b(snacks?|tea ?time|chai time|evening (bite|snack)|munchies|mid[- ]?(morning|day))\b""" to MealSlotType.SNACK,
        """\b(breakfast|brekkie|nashta|naashta|tiffin|brunch|morning)\b""" to MealSlotType.BREAKFAST,
        """\b(lunch|lunchbox|afternoon meal|noon)\b""" to MealSlotType.LUNCH,
        """\b(dinner|supper|tonight|night|evening meal)\b""" to MealSlotType.DINNER
    ).map { (pattern, type) -> Regex(pattern) to type }

    fun detect(text: String): MealSlotType? {
        val t = text.lowercase(Locale.ROOT)
        return RULES.firstOrNull { (re, _) -> re.containsMatchIn(t) }?.second
    }

    /**
     * The meal the user is most likely ordering for right now: a slot that's due, else the next
     * upcoming one, else the last planned meal of the day (late orders count toward dinner).
     */
    fun nextSlot(plan: List<MealSlot>): MealSlotType? {
        val planned = plan.filterNot { ScheduleRules.EXTRA_TAG in it.tags }
        return planned.firstOrNull { it.status == SlotStatus.DUE_NOW }?.type
            ?: planned.firstOrNull { it.status == SlotStatus.UPCOMING }?.type
            ?: planned.lastOrNull { it.type != MealSlotType.PRE_SLEEP }?.type
            ?: planned.lastOrNull()?.type
    }
}
