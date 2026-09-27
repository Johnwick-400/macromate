package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DayClock
import com.example.domain.analytics.DayKind
import com.example.domain.analytics.EstimateLevel
import com.example.domain.analytics.ActivitySummary
import com.example.domain.analytics.PeriodSummary
import com.example.domain.analytics.TrendPoint
import com.example.domain.budget.BudgetStatus
import com.example.domain.schedule.MealSlot
import com.example.domain.schedule.MealSlotType
import com.example.data.model.MacroLog
import com.example.domain.schedule.SlotStatus
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.LightTeal
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.OrangeSwiggy
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateDark
import com.example.ui.theme.SoftRed
import com.example.ui.theme.TextHint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.tactilePress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------------------------

internal fun fmtInt(value: Int): String = "%,d".format(value)

/** "Tue 22" */
internal fun fmtDayShort(date: LocalDate): String =
    "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.dayOfMonth}"

/** Signed kcal with a real minus sign: "+200", "−100". */
private fun fmtSignedInt(value: Int): String =
    if (value >= 0) "+${fmtInt(value)}" else "−${fmtInt(abs(value))}"

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Collapses consecutive dates into ranges: "Tue 22 – Thu 24, Sat 26". */
private fun fmtDateRanges(dates: List<LocalDate>): String {
    val sorted = dates.sorted()
    if (sorted.isEmpty()) return ""
    val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
    var start = sorted.first()
    var end = start
    for (d in sorted.drop(1)) {
        if (d == end.plusDays(1)) end = d else {
            ranges += start to end
            start = d
            end = d
        }
    }
    ranges += start to end
    return ranges.joinToString(", ") { (a, b) ->
        if (a == b) fmtDayShort(a) else "${fmtDayShort(a)} – ${fmtDayShort(b)}"
    }
}

// ---------------------------------------------------------------------------------------------
// Local persistence for the water counter and food-group checklist
// ---------------------------------------------------------------------------------------------




// ---------------------------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------------------------

/** Plain grouped container: white surface, hairline border, no elevation. */
@Composable
private fun DlGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) { content() }
}

@Composable
private fun DlEyebrow(text: String, color: Color = TextSecondary) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 1.2.sp
    )
}

@Composable
internal fun DietaryLogSectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
internal fun DietaryLogEmptyNote(text: String) {
    DlGroup {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextHint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        )
    }
}

@Composable
private fun DlProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "dlProgress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(SlateBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
private fun DlTag(text: String, background: Color = LightTeal, color: Color = NeonEmerald) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------------
// 1. Header
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogHeader(
    subtitle: String,
    calendarOpen: Boolean,
    onToggleCalendar: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateCard)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Full-colour approved icon; Color.Unspecified keeps its own colours (no tint).
            Icon(
                painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.ic_dietary_log_color),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dietary Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            IconButton(onClick = onToggleCalendar) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = if (calendarOpen) "Show week" else "Show month calendar",
                    tint = if (calendarOpen) NeonEmerald else TextSecondary
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Meal schedule settings", tint = TextSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Week strip
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogWeekStrip(
    selectedDate: LocalDate,
    today: LocalDate,
    kinds: Map<LocalDate, DayKind>,
    onSelect: (LocalDate) -> Unit
) {
    val weekStart = DayClock.weekStart(selectedDate)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        (0L until 7L).forEach { offset ->
            val date = weekStart.plusDays(offset)
            val isFuture = date.isAfter(today)
            val isSelected = date == selectedDate
            val kind = if (isFuture) null else kinds[date]
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) NeonEmerald else SlateCard)
                    .border(1.dp, if (isSelected) NeonEmerald else SlateBorder, RoundedCornerShape(12.dp))
                    .then(
                        if (isFuture) Modifier.alpha(0.45f)
                        else Modifier.clickable(role = Role.Button) { onSelect(date) }
                    )
                    .semantics {
                        contentDescription = "$dayName ${date.dayOfMonth}"
                        stateDescription = when {
                            isFuture -> "Upcoming"
                            else -> dayKindLabel(kind)
                        }
                    }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = dayName.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else TextHint
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else if (date == today) NeonEmerald else TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                DayStatusDot(kind = kind, onDark = isSelected)
            }
        }
    }
}

private fun dayKindLabel(kind: DayKind?): String = when (kind) {
    DayKind.COMPLETE -> "Logged"
    DayKind.FASTED -> "Fasted"
    DayKind.ESTIMATED -> "Estimated"
    DayKind.PARTIAL -> "Partly logged"
    DayKind.UNTRACKED -> "Not logged"
    DayKind.IN_PROGRESS -> "In progress"
    null -> "No data"
}

@Composable
private fun DayStatusDot(kind: DayKind?, onDark: Boolean) {
    val grey = if (onDark) Color.White.copy(alpha = 0.7f) else TextHint
    val teal = if (onDark) Color.White else NeonEmerald
    Canvas(modifier = Modifier.size(8.dp)) {
        val r = size.minDimension / 2
        val stroke = Stroke(width = 1.5.dp.toPx())
        when (kind) {
            DayKind.COMPLETE -> drawCircle(teal, r)
            DayKind.FASTED -> drawCircle(if (onDark) Color.White else ElectricIndigo, r)
            DayKind.ESTIMATED -> drawCircle(GoldenAmber, r - stroke.width / 2, style = stroke)
            DayKind.PARTIAL -> drawCircle(GoldenAmber, r)
            DayKind.UNTRACKED -> drawCircle(grey, r - stroke.width / 2, style = stroke)
            DayKind.IN_PROGRESS -> {
                drawCircle(teal, r - stroke.width / 2, style = stroke)
                drawCircle(teal, r * 0.35f)
            }
            null -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3. "What happened?" for unlogged days
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogWhatHappenedCard(
    days: List<LocalDate>,
    onEstimate: (LocalDate, EstimateLevel) -> Unit,
    onFasted: (LocalDate) -> Unit,
    onSkip: (LocalDate) -> Unit
) {
    val sorted = days.sorted()
    val day = sorted.first()
    DlGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            DlEyebrow("Fill in the gaps", color = NeonEmerald)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You didn't log ${fmtDateRanges(sorted)}. What happened?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "Life happens. A rough guess keeps your averages honest, and it only takes a tap.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (sorted.size > 1) "${fmtDayShort(day)} (1 of ${sorted.size})" else fmtDayShort(day),
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "Lighter" to EstimateLevel.LIGHTER,
                    "As planned" to EstimateLevel.AS_PLANNED,
                    "Heavier" to EstimateLevel.HEAVIER
                ).forEach { (label, level) ->
                    OutlinedButton(
                        onClick = { onEstimate(day, level) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(label, color = NeonEmerald, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { onFasted(day) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("I fasted", color = ElectricIndigo, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { onSkip(day) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Skip", color = TextSecondary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4. Day summary
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogDaySummary(
    date: LocalDate,
    isToday: Boolean,
    eatenKcal: Int,
    targetKcal: Int,
    eatenProtein: Int,
    targetProtein: Int,
    carryOverKcal: Int
) {
    DlGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            DlEyebrow(if (isToday) "Today" else fmtDayShort(date), color = NeonEmerald)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SummaryMetric(
                    label = "Energy",
                    value = "${fmtInt(eatenKcal)} / ${fmtInt(targetKcal)} kcal",
                    progress = eatenKcal.toFloat() / targetKcal.coerceAtLeast(1),
                    color = if (eatenKcal > targetKcal * 1.1) SoftRed else NeonEmerald,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "Protein",
                    value = "$eatenProtein / $targetProtein g",
                    progress = eatenProtein.toFloat() / targetProtein.coerceAtLeast(1),
                    color = ElectricIndigo,
                    modifier = Modifier.weight(1f)
                )
            }
            if (carryOverKcal != 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${fmtSignedInt(carryOverKcal)} kcal carried over from this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, progress: Float, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        DlProgressBar(progress = progress, color = color)
    }
}

// ---------------------------------------------------------------------------------------------
// 5. Meal timeline
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogMealTimeline(
    slots: List<MealSlot>,
    isToday: Boolean,
    onFindOnSwiggy: (MealSlot) -> Unit,
    onLog: (MealSlot) -> Unit,
    logsById: Map<Int, MacroLog> = emptyMap(),
    onMoveLog: (Int, MealSlotType) -> Unit = { _, _ -> }
) {
    if (slots.isEmpty()) {
        DietaryLogEmptyNote("No meals planned for this day.")
        return
    }
    DlGroup {
        slots.forEachIndexed { index, slot ->
            if (index > 0) HorizontalDivider(color = SlateBorder)
            MealSlotRow(
                slot = slot, isToday = isToday, onFindOnSwiggy = onFindOnSwiggy, onLog = onLog,
                logsById = logsById, onMoveLog = onMoveLog
            )
        }
    }
}

@Composable
private fun MealSlotRow(
    slot: MealSlot,
    isToday: Boolean,
    onFindOnSwiggy: (MealSlot) -> Unit,
    onLog: (MealSlot) -> Unit,
    logsById: Map<Int, MacroLog> = emptyMap(),
    onMoveLog: (Int, MealSlotType) -> Unit = { _, _ -> }
) {
    val isExtra = com.example.domain.schedule.ScheduleRules.EXTRA_TAG in slot.tags
    val status = slot.status
    val isDue = isToday && status == SlotStatus.DUE_NOW
    val isMissed = isToday && status == SlotStatus.MISSED
    val isLogged = status == SlotStatus.LOGGED
    val contentAlpha = if (isMissed) 0.6f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (isDue) OrangeSwiggy.copy(alpha = 0.06f) else Color.Transparent)
    ) {
        // Accent rail: orange when due, teal when logged
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    when {
                        isDue -> OrangeSwiggy
                        isLogged -> NeonEmerald
                        else -> Color.Transparent
                    }
                )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp, end = 14.dp, top = 12.dp, bottom = if (isToday) 4.dp else 12.dp)
                .alpha(contentAlpha)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.time.format(HHMM),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isDue) OrangeSwiggy else TextSecondary,
                    modifier = Modifier.widthIn(min = 48.dp)
                )
                Text(
                    text = slot.type.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                SlotStatusLabel(status = status, isToday = isToday)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.padding(start = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${fmtInt(slot.targetKcal)} kcal · ${slot.targetProteinG} g protein",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                slot.tags.filter { it == "pre-workout" || it == "post-workout" }.forEach { DlTag(it) }
                if (isExtra) DlTag("extra · not in plan")
            }
            if (slot.loggedKcal > 0) {
                Text(
                    text = "Logged ${fmtInt(slot.loggedKcal)} kcal · ${slot.loggedProteinG} g protein",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonEmerald,
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp)
                )
            }
            // What's in this section, each movable to another section if it landed wrong.
            slot.loggedIds.mapNotNull { logsById[it] }.forEach { log ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp)
                ) {
                    Text(
                        text = "${log.name} · ${fmtInt(log.calories)} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    MealSlotPicker(
                        selected = slot.type,
                        onSelect = { target -> if (target != slot.type) onMoveLog(log.id, target) },
                        prefix = "Move · "
                    )
                }
            }
            if (slot.suggestion.isNotBlank() && !isLogged) {
                Text(
                    text = slot.suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHint,
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                )
            }
            if (isToday) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isLogged) {
                        TextButton(onClick = { onFindOnSwiggy(slot) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Find on Swiggy", color = OrangeSwiggy, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { onLog(slot) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (isLogged) "Log more" else "Log it", color = NeonEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotStatusLabel(status: SlotStatus, isToday: Boolean) {
    when {
        status == SlotStatus.LOGGED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Logged", style = MaterialTheme.typography.labelSmall, color = NeonEmerald, fontWeight = FontWeight.Bold)
        }
        isToday && status == SlotStatus.DUE_NOW ->
            DlTag("Due now", background = OrangeSwiggy.copy(alpha = 0.14f), color = OrangeSwiggy)
        isToday && status == SlotStatus.MISSED ->
            Text("Missed", style = MaterialTheme.typography.labelSmall, color = TextHint)
        isToday -> Text("Upcoming", style = MaterialTheme.typography.labelSmall, color = TextHint)
        else -> Text("Not logged", style = MaterialTheme.typography.labelSmall, color = TextHint)
    }
}

// ---------------------------------------------------------------------------------------------
// 6. Warnings & tips
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogGuidance(warnings: List<String>, tips: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (warnings.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GoldenAmber.copy(alpha = 0.07f))
                    .border(1.dp, GoldenAmber.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = GoldenAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    warnings.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }
            }
        }
        tips.forEach { tip ->
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("·", style = MaterialTheme.typography.bodyMedium, color = NeonEmerald, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(tip, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 7. Water counter
// ---------------------------------------------------------------------------------------------

private const val WATER_GLASSES = 8

@Composable
internal fun DietaryLogWaterCounter(glasses: Int, onChange: (Int) -> Unit) {
    DlGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DlEyebrow("Water")
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$glasses of $WATER_GLASSES glasses",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (glasses >= WATER_GLASSES) NeonEmerald else TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..WATER_GLASSES).forEach { n ->
                    val filled = n <= glasses
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp, 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .tactilePress { onChange(if (glasses == n) n - 1 else n) }
                            .semantics {
                                contentDescription = "Glass $n"
                                stateDescription = if (filled) "Filled" else "Empty"
                            }
                    ) {
                        Icon(
                            imageVector = if (filled) Icons.Filled.LocalDrink else Icons.Outlined.LocalDrink,
                            contentDescription = null,
                            tint = if (filled) ElectricIndigo else SlateBorder,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "About 8 glasses (2 L) a day, more on training days.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}


// ---------------------------------------------------------------------------------------------
// 9. Week & month analytics
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogAnalyticsCard(
    week: PeriodSummary?,
    month: PeriodSummary?,
    weekActivity: ActivitySummary?,
    monthActivity: ActivitySummary?
) {
    DlGroup {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            PeriodColumn("This week", week, weekActivity, Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp)
                    .background(SlateBorder)
            )
            PeriodColumn("This month", month, monthActivity, Modifier.weight(1f))
        }
    }
}


@Composable
private fun PeriodColumn(title: String, summary: PeriodSummary?, activity: ActivitySummary?, modifier: Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DlEyebrow(title)
        Spacer(modifier = Modifier.height(4.dp))
        val meals = activity?.mealsLogged ?: 0
        val water = activity?.waterGlasses ?: 0
        if (meals == 0 && water == 0 && (summary?.validDays ?: 0) == 0) {
            Text("Log a meal or a glass of water to start.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            return@Column
        }
        // Activity counts start from the very first meal / glass, today included.
        Text(
            "$meals meal${if (meals == 1) "" else "s"} · ${activity?.daysWithMeals ?: 0} day${if ((activity?.daysWithMeals ?: 0) == 1) "" else "s"}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        val avgWater = activity?.avgWaterPerDay
        Text(
            if (avgWater != null) "Water: $water glass${if (water == 1) "" else "es"} · avg ${"%.1f".format(avgWater)}/day"
            else "Water: none logged",
            style = MaterialTheme.typography.bodySmall,
            color = if (water > 0) ElectricIndigo else TextSecondary
        )
        // Day-quality numbers use finished days only; before that, show today's running total.
        if (summary != null && summary.validDays > 0) {
            val judged = summary.validDays - summary.fastedDays
            if (judged > 0) {
                Text("On target: ${summary.onTargetDays} of $judged days", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            summary.avgKcal?.let {
                Text("Avg ${fmtInt(it)} kcal/day", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            summary.avgProteinG?.let {
                Text("Avg $it g protein/day", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (summary.fastedDays > 0) {
                Text("${summary.fastedDays} fasted", style = MaterialTheme.typography.bodySmall, color = ElectricIndigo)
            }
            if (!summary.enoughData) {
                Text("Early numbers — firmer after 4 logged days a week", style = MaterialTheme.typography.labelSmall, color = TextHint)
            }
        } else if ((activity?.todayKcal ?: 0) > 0) {
            Text("Today so far: ${fmtInt(activity!!.todayKcal)} kcal", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Averages start after your first full day", style = MaterialTheme.typography.labelSmall, color = TextHint)
        }
    }
}


// ---------------------------------------------------------------------------------------------
// 10. Budget mini row
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogBudgetRow(
    status: BudgetStatus,
    onAddAmount: (LocalDate) -> Unit,
    onNoSpend: (LocalDate) -> Unit,
    onReconcile: () -> Unit
) {
    val allowance = status.dailyAllowance
    val pendingDay = status.unconfirmedSpendDays.maxOrNull()
    DlGroup {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = if (allowance == null) SoftRed else OrangeSwiggy,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allowance != null) {
                        "₹${fmtInt(allowance.roundToInt())}/day safe to spend · ${status.daysLeft} " +
                            if (status.daysLeft == 1) "day left" else "days left"
                    } else "Monthly budget used up",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (allowance == null) SoftRed else TextPrimary
                )
                Text(
                    "₹${fmtInt(status.spent.roundToInt())} of ₹${fmtInt(status.budgetForPeriod.roundToInt())} spent",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                DlProgressBar(
                    progress = status.fractionUsed.toFloat(),
                    color = if (allowance == null) SoftRed else OrangeSwiggy
                )
                TextButton(
                    onClick = onReconcile,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 40.dp)
                ) { Text("Numbers look off? Reconcile month", color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (pendingDay != null) {
            HorizontalDivider(color = SlateBorder)
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                Text(
                    "Did you order food elsewhere on ${fmtDayShort(pendingDay)}?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                if (status.unconfirmedSpendDays.size > 1) {
                    Text(
                        "${status.unconfirmedSpendDays.size - 1} more day(s) to check after this",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextHint
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onNoSpend(pendingDay) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("No spend", color = TextSecondary)
                    }
                    TextButton(onClick = { onAddAmount(pendingDay) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Add amount", color = OrangeSwiggy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 11. Weight
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogWeightCard(
    points: List<TrendPoint>,
    weeklyRateKg: Double?,
    onLogWeight: (Double) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val latest = points.lastOrNull()

    DlGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            DlEyebrow("Weight trend")
            Spacer(modifier = Modifier.height(8.dp))
            if (latest != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.1f".format(latest.trendKg),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("kg", style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                        modifier = Modifier.padding(bottom = 3.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    weeklyRateKg?.let { rate ->
                        val sign = if (rate >= 0) "+" else "−"
                        Text(
                            "$sign${"%.1f".format(abs(rate))} kg/week",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                if (points.size >= 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WeightSparkline(points = points.takeLast(30))
                }
            } else {
                Text(
                    "Weigh in a few mornings a week to see a smoothed trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.filter { c -> c.isDigit() || c == '.' }.take(5)
                        error = null
                    },
                    label = { Text("Today's weight (kg)") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SlateDark, unfocusedContainerColor = SlateDark,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val kg = input.toDoubleOrNull()
                        if (kg == null || kg !in 25.0..300.0) {
                            error = "Enter a weight between 25 and 300 kg"
                        } else {
                            onLogWeight(kg)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Log weight") }
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = SoftRed, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun WeightSparkline(points: List<TrendPoint>) {
    val values = points.map { it.trendKg } + points.mapNotNull { it.measuredKg }
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0.2 } ?: 0.2
    val mid = (max + min) / 2
    val lo = mid - range / 2
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "Weight trend over the last ${points.size} days" }
    ) {
        val pad = 4.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun x(i: Int) = pad + if (points.size <= 1) w / 2 else w * i / (points.size - 1)
        fun y(v: Double) = pad + h - (h * ((v - lo) / range)).toFloat()

        points.forEachIndexed { i, p ->
            p.measuredKg?.let { drawCircle(TextHint.copy(alpha = 0.6f), radius = 2.dp.toPx(), center = Offset(x(i), y(it))) }
        }
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(i), y(p.trendKg)) else path.lineTo(x(i), y(p.trendKg))
        }
        drawPath(path, NeonEmerald, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(NeonEmerald, radius = 3.5.dp.toPx(), center = Offset(x(points.lastIndex), y(points.last().trendKg)))
    }
}

// ---------------------------------------------------------------------------------------------
// 13. Disclaimer
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogDisclaimer() {
    Text(
        "Guidance for healthy adults, not medical advice.",
        style = MaterialTheme.typography.bodySmall,
        color = TextHint,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}
