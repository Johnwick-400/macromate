package com.example.domain.analytics

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class WeightPoint(val date: LocalDate, val weightKg: Double)

data class TrendPoint(val date: LocalDate, val measuredKg: Double?, val trendKg: Double, val interpolated: Boolean)

/**
 * Smoothed weight trend (Hacker's Diet EWMA, 10% smoothing). Missed weigh-ins are filled by
 * straight-line interpolation first, as MacroFactor does (151 Mon, 150 Wed -> Tue 150.5),
 * so gaps don't bend the trend.
 */
object WeightTrend {
    const val SMOOTHING = 0.1

    fun compute(entries: List<WeightPoint>): List<TrendPoint> {
        val sorted = entries.sortedBy { it.date }.distinctBy { it.date }
        if (sorted.isEmpty()) return emptyList()

        val daily = mutableListOf<Triple<LocalDate, Double, Boolean>>() // date, weight, interpolated
        sorted.zipWithNext().forEach { (a, b) ->
            val gap = ChronoUnit.DAYS.between(a.date, b.date)
            daily += Triple(a.date, a.weightKg, false)
            for (d in 1 until gap) {
                val w = a.weightKg + (b.weightKg - a.weightKg) * d / gap
                daily += Triple(a.date.plusDays(d), w, true)
            }
        }
        daily += Triple(sorted.last().date, sorted.last().weightKg, false)

        var trend = daily.first().second
        return daily.map { (date, w, interp) ->
            trend += SMOOTHING * (w - trend)
            TrendPoint(date, if (interp) null else w, trend, interp)
        }
    }

    /** Change in trend weight per week over the last [days] days, or null without enough data. */
    fun weeklyRateKg(points: List<TrendPoint>, days: Int = 14): Double? {
        if (points.size < 7) return null
        val window = points.takeLast(days.coerceAtMost(points.size))
        val span = ChronoUnit.DAYS.between(window.first().date, window.last().date)
        if (span < 6) return null
        return (window.last().trendKg - window.first().trendKg) * 7.0 / span
    }
}
