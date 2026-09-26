package com.example

import com.example.data.model.DailyTargetSnapshot
import com.example.data.model.MacroLog
import com.example.data.repository.DaySummary
import com.example.data.repository.DayTrackingEngine
import com.example.data.repository.DayTrackingStatus
import org.junit.Assert.*
import org.junit.Test

class DayTrackingTest {

    @Test
    fun untrackedDayNeverCountsInAverages() {
        val summary = DayTrackingEngine.classifyDay(
            date = "2026-09-20",
            logs = emptyList()
        )
        assertEquals(DayTrackingStatus.UNTRACKED, summary.status)
        assertFalse(summary.isValidForAverages)
        assertEquals(0, summary.caloriesConsumed)
    }

    @Test
    fun completeDayCountsInAverages() {
        val logs = listOf(
            MacroLog(id = 1, userId = "u1", dateString = "2026-09-20", name = "Lunch", calories = 600, proteinG = 40, carbsG = 60, fatG = 15),
            MacroLog(id = 2, userId = "u1", dateString = "2026-09-20", name = "Dinner", calories = 700, proteinG = 45, carbsG = 70, fatG = 20)
        )
        val summary = DayTrackingEngine.classifyDay(
            date = "2026-09-20",
            logs = logs
        )
        assertEquals(DayTrackingStatus.COMPLETE, summary.status)
        assertTrue(summary.isValidForAverages)
        assertEquals(1300, summary.caloriesConsumed)
    }

    @Test
    fun partialDayIsNotCountedInAverages() {
        val logs = listOf(
            MacroLog(id = 1, userId = "u1", dateString = "2026-09-20", name = "Small Snack", calories = 250, proteinG = 10, carbsG = 25, fatG = 5)
        )
        val summary = DayTrackingEngine.classifyDay(
            date = "2026-09-20",
            logs = logs
        )
        assertEquals(DayTrackingStatus.PARTIAL, summary.status)
        assertFalse(summary.isValidForAverages)
    }

    @Test
    fun weeklyReportReturnsNotEnoughDataWhenLessThanFourValidDays() {
        val days = listOf(
            DaySummary("2026-09-15", DayTrackingStatus.COMPLETE, 2000, 140, 200, 60, 2000, 140, true, true),
            DaySummary("2026-09-16", DayTrackingStatus.COMPLETE, 2100, 145, 210, 65, 2000, 140, true, true),
            DaySummary("2026-09-17", DayTrackingStatus.UNTRACKED, 0, 0, 0, 0, 2000, 140, false, false),
            DaySummary("2026-09-18", DayTrackingStatus.UNTRACKED, 0, 0, 0, 0, 2000, 140, false, false),
            DaySummary("2026-09-19", DayTrackingStatus.PARTIAL, 300, 15, 30, 10, 2000, 140, false, false),
            DaySummary("2026-09-20", DayTrackingStatus.UNTRACKED, 0, 0, 0, 0, 2000, 140, false, false),
            DaySummary("2026-09-21", DayTrackingStatus.UNTRACKED, 0, 0, 0, 0, 2000, 140, false, false)
        )
        val report = DayTrackingEngine.computeWeeklyReport(days)
        assertFalse(report.hasEnoughData)
        assertNull(report.averageDailyCalories)
        assertNull(report.adherencePercent)
        assertEquals(2, report.validDaysCount)
    }
}
