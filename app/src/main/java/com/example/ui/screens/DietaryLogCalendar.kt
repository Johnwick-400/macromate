package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.DayKind
import com.example.domain.analytics.PeriodSummary
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.TextHint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month view of the Dietary Log: every day since sign-up with its status dot, month-to-month
 * navigation, and the month's summary underneath. Tapping a day opens it below.
 */
@Composable
internal fun DietaryLogMonthCalendar(
    month: YearMonth,
    today: LocalDate,
    trackingStart: LocalDate?,
    selectedDate: LocalDate,
    kinds: Map<LocalDate, DayKind>,
    summary: PeriodSummary?,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit
) {
    val current = YearMonth.from(today)
    val earliest = trackingStart?.let(YearMonth::from) ?: current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }, enabled = month > earliest) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = month < current) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        // Weekday header, Monday first (matches the week strip and weekly analytics).
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    text = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date -> CalendarDay(date, today, trackingStart, selectedDate, kinds[date], onSelect, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }

        HorizontalDivider(color = SlateBorder, modifier = Modifier.padding(top = 8.dp))
        Text(
            text = monthSummaryLine(summary),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        CalendarLegend()
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate?,
    today: LocalDate,
    trackingStart: LocalDate?,
    selectedDate: LocalDate,
    kind: DayKind?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier
) {
    if (date == null) {
        Spacer(modifier = modifier.aspectRatio(1f))
        return
    }
    val selectable = !date.isAfter(today) && (trackingStart == null || !date.isBefore(trackingStart))
    val selected = date == selectedDate
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) NeonEmerald else Color.Transparent)
            .then(if (selectable) Modifier.clickable(role = Role.Button) { onSelect(date) } else Modifier.alpha(0.35f))
            .semantics {
                contentDescription = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
                stateDescription = when {
                    !selectable -> "Unavailable"
                    else -> calendarKindLabel(kind)
                }
            }
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (date == today || selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> Color.White
                date == today -> NeonEmerald
                else -> TextPrimary
            }
        )
        Spacer(modifier = Modifier.height(2.dp))
        CalendarDot(kind = if (selectable) kind else null, onDark = selected)
    }
}

@Composable
private fun CalendarDot(kind: DayKind?, onDark: Boolean) {
    val teal = if (onDark) Color.White else NeonEmerald
    val grey = if (onDark) Color.White.copy(alpha = 0.7f) else TextHint
    Canvas(modifier = Modifier.size(6.dp)) {
        val r = size.minDimension / 2
        val stroke = Stroke(width = 1.2.dp.toPx())
        when (kind) {
            DayKind.COMPLETE -> drawCircle(teal, r)
            DayKind.FASTED -> drawCircle(if (onDark) Color.White else ElectricIndigo, r)
            DayKind.ESTIMATED -> drawCircle(GoldenAmber, r - stroke.width / 2, style = stroke)
            DayKind.PARTIAL -> drawCircle(GoldenAmber, r)
            DayKind.UNTRACKED -> drawCircle(grey, r - stroke.width / 2, style = stroke)
            DayKind.IN_PROGRESS -> drawCircle(teal, r - stroke.width / 2, style = stroke)
            null -> Unit
        }
    }
}

@Composable
private fun CalendarLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        listOf(DayKind.COMPLETE to "Logged", DayKind.PARTIAL to "Partly", DayKind.FASTED to "Fasted", DayKind.UNTRACKED to "Missed")
            .forEach { (kind, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) { CalendarDot(kind, onDark = false) }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = TextHint)
                }
            }
    }
}

private fun calendarKindLabel(kind: DayKind?): String = when (kind) {
    DayKind.COMPLETE -> "Logged"
    DayKind.FASTED -> "Fasted"
    DayKind.ESTIMATED -> "Estimated"
    DayKind.PARTIAL -> "Partly logged"
    DayKind.UNTRACKED -> "Not logged"
    DayKind.IN_PROGRESS -> "Today, in progress"
    null -> "No data"
}

private fun monthSummaryLine(summary: PeriodSummary?): String {
    if (summary == null) return "Nothing logged this month yet."
    val parts = mutableListOf("Logged ${summary.validDays} of ${summary.elapsedDays} days")
    val judged = summary.validDays - summary.fastedDays
    if (judged > 0) parts += "on target ${summary.onTargetDays} of $judged"
    summary.avgKcal?.let { parts += "avg ${fmtInt(it)} kcal" }
    return parts.joinToString(" · ")
}
