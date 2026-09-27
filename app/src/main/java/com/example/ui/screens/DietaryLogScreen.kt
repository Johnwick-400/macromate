package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.DayStatus
import com.example.domain.analytics.WeightTrend
import com.example.domain.schedule.MealSlot
import com.example.ui.theme.SlateDark
import com.example.ui.viewmodel.DietaryLogViewModel
import com.example.ui.viewmodel.MacroMateViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Dietary Log tab: the day's meal timeline, quick "what happened?" answers for missed days,
 * water and food-group checklists, week/month analytics, budget and weight trend.
 * [onFindOnSwiggy] sends a prefilled query to the Assistant tab and switches to it.
 */
@Composable
fun DietaryLogScreen(
    viewModel: MacroMateViewModel,
    dietVm: DietaryLogViewModel,
    onFindOnSwiggy: (query: String, slot: com.example.data.model.MealSlotContext) -> Unit
) {
    val profile by dietVm.profile.collectAsState()
    val today by dietVm.today.collectAsState()
    val selectedDate by dietVm.selectedDate.collectAsState()
    val dayPlan by dietVm.dayPlan.collectAsState()
    val weekSummary by dietVm.weekSummary.collectAsState()
    val monthSummary by dietVm.monthSummary.collectAsState()
    val daysNeedingAnswer by dietVm.daysNeedingAnswer.collectAsState()
    val classifiedDays by dietVm.classifiedDays.collectAsState()
    val budgetStatus by dietVm.budgetStatus.collectAsState()
    val weightTrend by dietVm.weightTrend.collectAsState()
    val carryOverKcal by dietVm.todayCarryOverKcal.collectAsState()
    val userId by viewModel.currentUserId.collectAsState()
    val safetyReasons by dietVm.safetyReasons.collectAsState()
    val selectedDayLogs by dietVm.selectedDayLogs.collectAsState()
    val visibleMonth by dietVm.visibleMonth.collectAsState()
    val trackingStart by dietVm.trackingStart.collectAsState()
    val visibleMonthSummary by dietVm.visibleMonthSummary.collectAsState()
    val weekActivity by dietVm.weekActivity.collectAsState()
    val monthActivity by dietVm.monthActivity.collectAsState()
    val selectedDayWater by dietVm.selectedDayWater.collectAsState()
    var showCalendar by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var showReconcile by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Android 13+: reminders need the notification permission; ask once when they're switched on.
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* the reminder scheduler re-checks the permission on its own */ }

    var showSettings by remember { mutableStateOf(false) }
    var loggingSlot by remember { mutableStateOf<MealSlot?>(null) }
    var spendDate by remember { mutableStateOf<LocalDate?>(null) }

    val isToday = selectedDate == today
    val plan = dayPlan
    val slots = plan?.slots.orEmpty()
    val targetKcal = slots.sumOf { it.targetKcal }.takeIf { it > 0 } ?: profile?.dailyCalories ?: 2000
    val targetProtein = slots.sumOf { it.targetProteinG }.takeIf { it > 0 } ?: profile?.dailyProteinG ?: 0
    val eatenKcal = slots.sumOf { it.loggedKcal }
    val eatenProtein = slots.sumOf { it.loggedProteinG }
    val dayKinds = remember(classifiedDays) { classifiedDays.associate { it.date to it.kind } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        DietaryLogHeader(
            subtitle = profile?.let {
                "${it.goal} · ${fmtInt(it.dailyCalories)} kcal · ${it.dailyProteinG} g protein"
            } ?: "Set up your profile to see a plan",
            calendarOpen = showCalendar,
            onToggleCalendar = { showCalendar = !showCalendar },
            onOpenSettings = { showSettings = true }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "week") {
                if (showCalendar) {
                    DietaryLogMonthCalendar(
                        month = visibleMonth,
                        today = today,
                        trackingStart = trackingStart,
                        selectedDate = selectedDate,
                        kinds = dayKinds,
                        summary = visibleMonthSummary,
                        onSelect = dietVm::selectDate,
                        onMonthChange = dietVm::showMonth
                    )
                } else {
                    DietaryLogWeekStrip(
                        selectedDate = selectedDate,
                        today = today,
                        kinds = dayKinds,
                        onSelect = dietVm::selectDate
                    )
                }
            }

            if (safetyReasons.isNotEmpty()) {
                item(key = "safety") {
                    DietaryLogGuidance(
                        warnings = listOf(
                            "Because of ${safetyReasons.joinToString(", ")}, MacroMate won't carry calories " +
                                "across days or suggest fasting. Please plan fasts and calorie cuts with your doctor."
                        ),
                        tips = emptyList()
                    )
                }
            }

            if (isToday && daysNeedingAnswer.isNotEmpty()) {
                item(key = "what_happened") {
                    DietaryLogWhatHappenedCard(
                        days = daysNeedingAnswer,
                        onEstimate = { date, level -> dietVm.resolveDay(date, DayStatus.ESTIMATED, level) },
                        onFasted = { date -> dietVm.resolveDay(date, DayStatus.FASTED) },
                        onSkip = { date -> dietVm.resolveDay(date, DayStatus.SKIPPED) }
                    )
                }
            }

            item(key = "summary") {
                DietaryLogDaySummary(
                    date = selectedDate,
                    isToday = isToday,
                    eatenKcal = eatenKcal,
                    targetKcal = targetKcal,
                    eatenProtein = eatenProtein,
                    targetProtein = targetProtein,
                    carryOverKcal = if (isToday) carryOverKcal else 0
                )
            }

            item(key = "timeline_title") {
                DietaryLogSectionTitle(
                    title = if (isToday) "Today's meals" else "Meals on ${fmtDayShort(selectedDate)}",
                    trailing = if (slots.isNotEmpty()) "${slots.count { it.loggedKcal > 0 }} of ${slots.size} logged" else null
                )
            }
            item(key = "timeline") {
                if (plan == null) {
                    DietaryLogEmptyNote("Finish your profile (weight and goal) to get a meal plan for the day.")
                } else {
                    DietaryLogMealTimeline(
                        slots = slots,
                        isToday = isToday,
                        onFindOnSwiggy = { slot ->
                            val allowance = budgetStatus?.dailyAllowance?.roundToInt()
                            val vegetarian = profile?.allergiesJson.orEmpty().lowercase().let {
                                "vegetarian" in it || "vegan" in it
                            }
                            onFindOnSwiggy(
                                com.example.domain.schedule.SlotSearch.query(slot, vegetarian, selectedDate, allowance),
                                com.example.data.model.MealSlotContext(slot.type.name, slot.type.label)
                            )
                        },
                        onLog = { slot -> loggingSlot = slot },
                        logsById = selectedDayLogs.associateBy { it.id },
                        onMoveLog = { id, type -> dietVm.moveLog(id, type) }
                    )
                }
            }

            if (plan != null && (plan.warnings.isNotEmpty() || plan.tips.isNotEmpty())) {
                item(key = "guidance") {
                    DietaryLogGuidance(warnings = plan.warnings, tips = plan.tips)
                }
            }

            item(key = "water") {
                DietaryLogWaterCounter(glasses = selectedDayWater, onChange = { dietVm.setWater(selectedDate, it) })
            }

            item(key = "analytics") {
                DietaryLogAnalyticsCard(
                    week = weekSummary,
                    month = monthSummary,
                    weekActivity = weekActivity,
                    monthActivity = monthActivity
                )
            }

            budgetStatus?.let { status ->
                item(key = "budget") {
                    DietaryLogBudgetRow(
                        status = status,
                        onAddAmount = { date -> spendDate = date },
                        onNoSpend = { date -> dietVm.confirmNoSpend(date) },
                        onReconcile = { showReconcile = true }
                    )
                }
            }

            item(key = "weight") {
                DietaryLogWeightCard(
                    points = weightTrend,
                    weeklyRateKg = remember(weightTrend) { WeightTrend.weeklyRateKg(weightTrend) },
                    onLogWeight = { kg -> dietVm.logWeight(kg) }
                )
            }

            item(key = "disclaimer") {
                DietaryLogDisclaimer()
            }
        }
    }

    loggingSlot?.let { slot ->
        DietaryLogMealDialog(
            viewModel = viewModel,
            slot = slot,
            onDismiss = { loggingSlot = null }
        )
    }

    spendDate?.let { date ->
        DietaryLogOutsideSpendDialog(
            date = date,
            onDismiss = { spendDate = null },
            onSave = { amount, note ->
                dietVm.addOutsideSpend(date, amount, note)
                spendDate = null
            }
        )
    }

    val settingsProfile = profile
    if (showSettings && settingsProfile != null) {
        DietaryLogScheduleSheet(
            profile = settingsProfile,
            onDismiss = { showSettings = false },
            onSave = { meals, wake, sleep, workout, template, dayEnds, reminders, pregnant, diabetes, ed ->
                dietVm.updateSchedulePrefs(meals, wake, sleep, workout, template, dayEnds, reminders)
                dietVm.updateHealthChecks(pregnant, diabetes, ed)
                if (reminders && android.os.Build.VERSION.SDK_INT >= 33 &&
                    !com.example.notify.MealReminders.canPostNotifications(context)
                ) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                showSettings = false
            }
        )
    }

    val reconcileStatus = budgetStatus
    if (showReconcile && reconcileStatus != null) {
        DietaryLogReconcileDialog(
            knownSpend = reconcileStatus.spent,
            onDismiss = { showReconcile = false },
            onSave = { actual ->
                dietVm.reconcileMonth(actual)
                showReconcile = false
            }
        )
    }
}
