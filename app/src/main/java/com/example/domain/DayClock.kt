package com.example.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The app's notion of "which day is it". A meal eaten before [dayEndsAtHour] (default 4 AM)
 * belongs to the previous day, so a 1 AM snack after a late dinner doesn't open a new day.
 */
object DayClock {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd, matches MacroLog.dateString

    fun logicalDate(
        now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault()),
        dayEndsAtHour: Int = 4
    ): LocalDate {
        val hour = dayEndsAtHour.coerceIn(0, 6)
        return if (now.hour < hour) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    fun logicalDateString(now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault()), dayEndsAtHour: Int = 4): String =
        format(logicalDate(now, dayEndsAtHour))

    fun format(date: LocalDate): String = date.format(DATE)

    fun parse(dateString: String): LocalDate? = runCatching { LocalDate.parse(dateString, DATE) }.getOrNull()

    fun monthStart(date: LocalDate): LocalDate = YearMonth.from(date).atDay(1)

    fun monthEnd(date: LocalDate): LocalDate = YearMonth.from(date).atEndOfMonth()

    /** Monday of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
}
