package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MacroMateDatabase
import com.example.data.model.DayRecord
import com.example.data.model.DayStatus
import com.example.data.model.MacroLog
import com.example.data.model.OutsideSpendLog
import com.example.data.model.SpendKind
import com.example.data.model.UserProfile
import com.example.data.model.WeightLog
import com.example.data.repository.MacroMateRepository
import com.example.domain.DayClock
import com.example.domain.analytics.ActivityCounter
import com.example.domain.analytics.ActivitySummary
import com.example.domain.analytics.AdherenceCalculator
import com.example.domain.analytics.ClassifiedDay
import com.example.domain.analytics.DayClassifier
import com.example.domain.analytics.DayInput
import com.example.domain.analytics.DayTarget
import com.example.domain.analytics.EstimateLevel
import com.example.domain.analytics.PeriodSummary
import com.example.domain.analytics.TrendPoint
import com.example.domain.analytics.WeightPoint
import com.example.domain.analytics.WeightTrend
import com.example.domain.budget.BudgetEngine
import com.example.domain.budget.BudgetStatus
import com.example.domain.schedule.DayPlan
import com.example.domain.schedule.LoggedMeal
import com.example.domain.schedule.MealScheduleEngine
import com.example.domain.schedule.MealSlotType
import com.example.domain.schedule.ScheduleInput
import com.example.domain.schedule.ScheduleTemplate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * State for the Dietary Log tab and the Monthly Budget card. All rules live in the pure
 * engines under com.example.domain; this class only wires Room flows into them, so the
 * existing chat / basket / tracker flows in [MacroMateViewModel] are untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DietaryLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MacroMateRepository(MacroMateDatabase.getDatabase(application).dao())

    private val userId = MutableStateFlow<String?>(null)

    /** Called from MainActivity whenever the signed-in user changes. */
    fun bindUser(id: String?) {
        userId.value = id
    }

    val profile: StateFlow<UserProfile?> = userId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.getUserProfile(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Logical "today" (day ends at the profile's hour), re-checked every minute. */
    val today: StateFlow<LocalDate> = profile
        .map { it?.dayEndsAtHour ?: 4 }
        .distinctUntilChanged()
        .flatMapLatest { hour ->
            flow {
                while (true) {
                    emit(DayClock.logicalDate(dayEndsAtHour = hour))
                    delay(60_000L)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayClock.logicalDate())

    /** The day the user is looking at in the week strip (defaults to today). */
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate> = combine(_selectedDate, today) { sel, t -> sel ?: t }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayClock.logicalDate())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = if (date == today.value) null else date
    }

    /** Month shown in the calendar (defaults to the current month). */
    private val _visibleMonth = MutableStateFlow<YearMonth?>(null)
    val visibleMonth: StateFlow<YearMonth> = combine(_visibleMonth, today) { m, t -> m ?: YearMonth.from(t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearMonth.from(DayClock.logicalDate()))

    /** First day this user can have data (account creation). */
    val trackingStart: StateFlow<LocalDate?> = profile.map { it?.trackingStart() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun showMonth(month: YearMonth) {
        val current = YearMonth.from(today.value)
        val earliest = trackingStart.value?.let(YearMonth::from) ?: current
        _visibleMonth.value = month.coerceIn(earliest, current).takeIf { it != current }
    }

    /** Data window: covers the calendar month, the selected day's week and this week/month, up to today. */
    private data class Window(val userId: String, val from: LocalDate, val to: LocalDate)

    private val window = combine(userId, today, visibleMonth, selectedDate) { id, t, month, sel ->
        id?.let {
            val from = listOf(DayClock.monthStart(t), DayClock.weekStart(t), month.atDay(1), DayClock.weekStart(sel)).min()
            Window(it, from, t)
        }
    }.distinctUntilChanged()

    private val logs = window.flatMapLatest { w ->
        if (w == null) flowOf(emptyList())
        else repository.getMacroLogsBetween(w.userId, DayClock.format(w.from), DayClock.format(w.to))
    }

    private val dayRecords = window.flatMapLatest { w ->
        if (w == null) flowOf(emptyList())
        else repository.getDayRecordsBetween(w.userId, DayClock.format(w.from), DayClock.format(w.to))
    }

    private val spendEntries = window.flatMapLatest { w ->
        if (w == null) flowOf(emptyList())
        else repository.getOutsideSpendsBetween(w.userId, DayClock.format(w.from), DayClock.format(w.to))
    }

    init {
        // Snapshot today's targets the first time the day is seen, so later goal changes
        // never rewrite how this day is judged.
        viewModelScope.launch {
            combine(profile.filterNotNull(), today) { p, t -> p to t }
                .distinctUntilChanged()
                .collect { (p, t) ->
                    if (p.weightKg > 10.0) repository.snapshotDayIfAbsent(p.snapshotFor(t, DayStatus.AUTO))
                }
        }
    }

    // --- Day classification & analytics ---

    val classifiedDays: StateFlow<List<ClassifiedDay>> = combine(profile, today, window, logs, dayRecords) { p, t, w, l, r ->
        // Only days since the account exists: a new user is never asked about days before sign-up.
        if (p == null || w == null) emptyList() else classify(p, t, maxOf(w.from, p.trackingStart()), l, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekSummary: StateFlow<PeriodSummary?> = combine(classifiedDays, today) { days, t ->
        val start = DayClock.weekStart(t)
        days.filter { !it.date.isBefore(start) }.takeIf { it.isNotEmpty() }?.let(AdherenceCalculator::summarize)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val monthSummary: StateFlow<PeriodSummary?> = combine(classifiedDays, today) { days, t ->
        val start = DayClock.monthStart(t)
        days.filter { !it.date.isBefore(start) }.takeIf { it.isNotEmpty() }?.let(AdherenceCalculator::summarize)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val visibleMonthSummary: StateFlow<PeriodSummary?> = combine(classifiedDays, visibleMonth) { days, month ->
        days.filter { YearMonth.from(it.date) == month }.takeIf { it.isNotEmpty() }?.let(AdherenceCalculator::summarize)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val waterByDate = dayRecords.map { records ->
        records.mapNotNull { r -> DayClock.parse(r.dateString)?.let { it to r.waterGlasses } }.toMap()
    }

    private fun activityFor(from: (LocalDate) -> LocalDate) =
        combine(logs, waterByDate, today, profile) { l, water, t, p ->
            val own = l.filterNot { it.isShared }
            val start = maxOf(from(t), p?.trackingStart() ?: from(t))
            ActivityCounter.count(
                mealDates = own.mapNotNull { DayClock.parse(it.dateString) },
                waterByDate = water,
                from = start,
                to = t,
                today = t,
                todayKcal = own.filter { it.dateString == DayClock.format(t) }.sumOf { it.calories }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Counted from the first meal / first glass, today included. */
    val weekActivity: StateFlow<ActivitySummary?> = activityFor(DayClock::weekStart)
    val monthActivity: StateFlow<ActivitySummary?> = activityFor(DayClock::monthStart)

    /** Glasses of water for the selected day. */
    val selectedDayWater: StateFlow<Int> = combine(waterByDate, selectedDate) { water, sel -> water[sel] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Past days in this month the user hasn't told us about yet (drives the "What happened?" prompt). */
    val daysNeedingAnswer: StateFlow<List<LocalDate>> = combine(classifiedDays, today) { days, t ->
        val start = DayClock.monthStart(t)
        days.filter { it.needsAnswer && !it.date.isBefore(start) }.map { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Non-empty = carry-over and fasting nudges are paused and a "talk to your doctor" note shows. */
    val safetyReasons: StateFlow<List<String>> = profile.map { p ->
        if (p == null) emptyList() else com.example.domain.analytics.SafetyRules.reasons(
            p.age, p.weightKg, p.heightCm, p.isPregnantOrLactating, p.onDiabetesMeds, p.edHistory
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Today's kcal target after the capped weekly carry-over from known days (0 when safety-gated). */
    val todayCarryOverKcal: StateFlow<Int> = combine(classifiedDays, today, safetyReasons) { days, t, safety ->
        if (safety.isNotEmpty()) return@combine 0
        val todayDay = days.firstOrNull { it.date == t } ?: return@combine 0
        val weekBefore = days.filter { !it.date.isBefore(DayClock.weekStart(t)) && it.date.isBefore(t) }
        val daysLeft = 7 - (t.dayOfWeek.value - 1)
        AdherenceCalculator.carryOverKcal(weekBefore, todayDay.target.kcal, daysLeft)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Meal schedule for the selected day ---

    val dayPlan: StateFlow<DayPlan?> = combine(
        profile, selectedDate, today, logs,
        combine(classifiedDays, todayCarryOverKcal) { days, carry -> days to carry }
    ) { p, sel, t, l, (days, todayCarry) ->
        buildPlan(p, sel, t, l, days, todayCarry)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Today's plan regardless of the day being viewed (for picking a meal section right now). */
    val todayPlan: StateFlow<DayPlan?> = combine(
        profile, today, logs,
        combine(classifiedDays, todayCarryOverKcal) { days, carry -> days to carry }
    ) { p, t, l, (days, todayCarry) ->
        buildPlan(p, t, t, l, days, todayCarry)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The meal someone ordering right now is most likely ordering for: due, else next, else dinner. */
    val suggestedSlotNow: StateFlow<MealSlotType?> = todayPlan
        .map { plan -> plan?.let { com.example.domain.schedule.MealIntent.nextSlot(it.slots) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun buildPlan(
        p: UserProfile?, sel: LocalDate, t: LocalDate, l: List<MacroLog>, days: List<ClassifiedDay>, todayCarry: Int
    ): DayPlan? {
        if (p == null || p.weightKg <= 10.0) return null
        val target = days.firstOrNull { it.date == sel }?.target ?: p.targetFor()
        val carry = if (sel == t) todayCarry else 0
        val dayLogs = l.filter { it.dateString == DayClock.format(sel) && !it.isShared }
        return MealScheduleEngine.buildDay(
            input = p.scheduleInput(target, carry),
            logged = dayLogs.map { it.toLoggedMeal(p.dayEndsAtHour) },
            now = if (sel == t) LocalTime.now() else null
        )
    }

    val selectedDayLogs: StateFlow<List<MacroLog>> = combine(logs, selectedDate) { l, sel ->
        l.filter { it.dateString == DayClock.format(sel) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Keep offline meal reminders in step with today's plan (re-run whenever it changes:
        // new logs, schedule settings, or the reminders switch).
        viewModelScope.launch {
            combine(profile, todayPlan) { p, plan -> Triple(p, plan, true) }
                .collect { (p, plan, viewingToday) ->
                    if (p == null) return@collect
                    val app = getApplication<Application>()
                    val enabled = p.remindersEnabled && com.example.notify.MealReminders.canPostNotifications(app)
                    if (!enabled) com.example.notify.MealReminders.schedule(app, today.value, emptyList(), false)
                    else if (viewingToday && plan != null) com.example.notify.MealReminders.schedule(app, today.value, plan.slots, true)
                }
        }
    }

    // --- Budget ---

    val budgetStatus: StateFlow<BudgetStatus?> = combine(profile, today, logs, spendEntries, dayRecords) { p, t, l, s, r ->
        if (p == null || p.monthlyBudgetInr <= 0.0) return@combine null
        val start = DayClock.monthStart(t)
        val inMonth = { d: String -> DayClock.parse(d)?.let { !it.isBefore(start) && !it.isAfter(t) } == true }
        val spent = l.filter { inMonth(it.dateString) }.sumOf { it.price } +
            s.filter { inMonth(it.date) }.sumOf { it.amountInr }
        // A day has spend info if something was logged or recorded, or the user said "no spend".
        val known = buildSet {
            l.mapNotNullTo(this) { DayClock.parse(it.dateString) }
            s.mapNotNullTo(this) { DayClock.parse(it.date) }
            r.filter { it.noSpendConfirmed || it.status == DayStatus.FASTED.name }
                .mapNotNullTo(this) { DayClock.parse(it.dateString) }
            // Days before the account existed have nothing to confirm.
            var d = start
            while (d.isBefore(p.trackingStart())) { add(d); d = d.plusDays(1) }
        }
        BudgetEngine.status(p.monthlyBudgetInr, DayClock.parse(p.budgetStartDate), spent, t, known)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Weight ---

    val weightTrend: StateFlow<List<TrendPoint>> = userId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.getWeightLogsFlow(id) }
        .map { entries -> WeightTrend.compute(entries.mapNotNull { e -> DayClock.parse(e.date)?.let { WeightPoint(it, e.weightKg) } }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Actions ---

    fun setMonthlyBudget(amountInr: Double) = updateProfile { p ->
        val t = today.value
        // Setting a budget for the first time mid-month prorates it from today.
        val start = if (p.monthlyBudgetInr <= 0.0 && t.dayOfMonth > 1) DayClock.format(t) else p.budgetStartDate
        p.copy(monthlyBudgetInr = amountInr.coerceAtLeast(0.0), budgetStartDate = if (amountInr > 0) start else "")
    }

    fun updateSchedulePrefs(
        mealsPerDay: Int, wakeTime: String, sleepTime: String, workoutTime: String?,
        template: ScheduleTemplate, dayEndsAtHour: Int, remindersEnabled: Boolean
    ) = updateProfile {
        it.copy(
            mealsPerDay = mealsPerDay.coerceIn(2, 6),
            wakeTimeMinutes = toMinutes(wakeTime, it.wakeTimeMinutes),
            sleepTimeMinutes = toMinutes(sleepTime, it.sleepTimeMinutes),
            workoutEnabled = workoutTime != null,
            workoutTimeMinutes = workoutTime?.let { t -> toMinutes(t, it.workoutTimeMinutes) } ?: it.workoutTimeMinutes,
            isEarlyHeavyCut = template == ScheduleTemplate.FRONT_LOADED,
            dayEndsAtHour = dayEndsAtHour.coerceIn(0, 6), remindersEnabled = remindersEnabled
        )
    }

    /** "Male" or "Female" — makes the BMR on Profile exact. */
    fun setSex(sex: String) = updateProfile { it.copy(sex = sex) }

    fun updateHealthChecks(pregnantOrBreastfeeding: Boolean, onDiabetesMeds: Boolean, edHistory: Boolean) =
        updateProfile {
            it.copy(isPregnantOrLactating = pregnantOrBreastfeeding, onDiabetesMeds = onDiabetesMeds, edHistory = edHistory)
        }

    /** Answer for a missed/partial day: fasted, a quick estimate, or leave it unknown. */
    fun resolveDay(date: LocalDate, status: DayStatus, estimate: EstimateLevel? = null) = updateDay(date) { rec ->
        val kcal = estimate?.let { DayClassifier.quickEstimateKcal(rec.toTarget(), it) }
        rec.copy(status = status.name, estimatedKcal = if (status == DayStatus.ESTIMATED) kcal else null)
    }

    /** "Move to…": put a logged item in another section of the Dietary Log. */
    fun moveLog(logId: Int, slot: MealSlotType) = viewModelScope.launch { repository.updateMealSlot(logId, slot.name) }

    fun setWater(date: LocalDate, glasses: Int) = updateDay(date) { it.copy(waterGlasses = glasses.coerceIn(0, 20)) }

    fun confirmNoSpend(date: LocalDate) = updateDay(date) { it.copy(noSpendConfirmed = true) }

    fun addOutsideSpend(date: LocalDate, amountInr: Double, note: String?) = withUser { id ->
        if (amountInr > 0) repository.saveOutsideSpend(
            OutsideSpendLog(userId = id, date = DayClock.format(date), amountInr = amountInr,
                kind = SpendKind.OUTSIDE_ORDER.name, note = note.orEmpty().trim())
        )
    }

    /** Month-end reconcile: one adjustment entry makes known spend equal what the user actually spent. */
    fun reconcileMonth(actualSpendInr: Double) = withUser { id ->
        val status = budgetStatus.value ?: return@withUser
        val adjustment = BudgetEngine.reconcileAdjustment(status.spent, actualSpendInr)
        if (adjustment != 0.0) repository.saveOutsideSpend(
            OutsideSpendLog(userId = id, date = DayClock.format(today.value), amountInr = adjustment,
                kind = SpendKind.RECONCILE.name, note = "Month reconcile")
        )
    }

    fun deleteSpendEntry(id: Int) = viewModelScope.launch { repository.deleteOutsideSpend(id) }

    /** Out-of-range input is ignored — never replaced by a made-up weight. */
    fun logWeight(weightKg: Double, date: LocalDate = today.value) = withUser { id ->
        if (weightKg !in 25.0..300.0) return@withUser
        val trend = WeightTrend.compute(
            weightTrend.value.mapNotNull { p -> p.measuredKg?.let { WeightPoint(p.date, it) } }
                .filter { it.date != date } + WeightPoint(date, weightKg)
        ).lastOrNull()?.trendKg ?: weightKg
        repository.saveWeightForDay(WeightLog(userId = id, date = DayClock.format(date), weightKg = weightKg, trendWeightKg = trend))
    }

    // --- Helpers ---

    private fun withUser(block: suspend (String) -> Unit) {
        val id = userId.value ?: return
        viewModelScope.launch { block(id) }
    }

    private fun updateProfile(change: (UserProfile) -> UserProfile) = withUser {
        val p = profile.value ?: return@withUser
        repository.saveUserProfile(change(p))
    }

    private fun updateDay(date: LocalDate, change: (DayRecord) -> DayRecord) = withUser { id ->
        val p = profile.value ?: return@withUser
        val key = DayClock.format(date)
        val existing = repository.getDayRecord(id, key) ?: p.snapshotFor(date, DayStatus.AUTO)
        repository.saveDayRecord(change(existing))
    }

    private fun classify(
        p: UserProfile, t: LocalDate, from: LocalDate, logs: List<MacroLog>, records: List<DayRecord>
    ): List<ClassifiedDay> {
        val byDate = logs.groupBy { it.dateString }
        val recByDate = records.associateBy { it.dateString }
        val days = ChronoUnit.DAYS.between(from, t).toInt()
        return (0..days).map { i ->
            val date = from.plusDays(i.toLong())
            val key = DayClock.format(date)
            val own = byDate[key].orEmpty().filterNot { it.isShared }
            val rec = recByDate[key]
            DayClassifier.classify(
                DayInput(
                    date = date,
                    // Days before this feature existed have no snapshot; fall back to current targets.
                    target = rec?.toTarget() ?: p.targetFor(),
                    status = rec?.status?.let { runCatching { DayStatus.valueOf(it) }.getOrNull() } ?: DayStatus.AUTO,
                    estimatedKcal = rec?.estimatedKcal,
                    loggedKcal = own.sumOf { it.calories },
                    loggedProteinG = own.sumOf { it.proteinG },
                    mealTimes = own.map { it.localTime() },
                    isToday = date == t
                )
            )
        }
    }
}

// --- Mapping helpers (kept out of the class so they stay simple to read) ---

/** First day this user can have data: the day the account/profile was created. */
private fun UserProfile.trackingStart(): LocalDate =
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun UserProfile.targetFor() = DayTarget(dailyCalories, dailyProteinG, dailyCarbsG, dailyFatG)

private fun UserProfile.snapshotFor(date: LocalDate, status: DayStatus) = DayRecord(
    userId = userId, dateString = DayClock.format(date), status = status.name,
    targetKcal = dailyCalories, targetProteinG = dailyProteinG, targetCarbsG = dailyCarbsG,
    targetFatG = dailyFatG, goal = goal
)

private fun DayRecord.toTarget() = DayTarget(targetKcal, targetProteinG, targetCarbsG, targetFatG)

private fun toMinutes(hhmm: String, fallback: Int): Int =
    runCatching { LocalTime.parse(hhmm).let { it.hour * 60 + it.minute } }.getOrDefault(fallback)

private fun minutesToTime(minutes: Int): LocalTime = LocalTime.of((minutes / 60).mod(24), minutes.mod(60))

/** HH:mm views of the stored minute fields, for the schedule settings UI. */
val UserProfile.wakeTime: String get() = minutesToTime(wakeTimeMinutes).toString()
val UserProfile.sleepTime: String get() = minutesToTime(sleepTimeMinutes).toString()
val UserProfile.workoutTime: String? get() = if (workoutEnabled) minutesToTime(workoutTimeMinutes).toString() else null
val UserProfile.scheduleTemplate: String get() = if (isEarlyHeavyCut) ScheduleTemplate.FRONT_LOADED.name else ScheduleTemplate.BALANCED.name

private fun UserProfile.isVegetarian(): Boolean = runCatching {
    val arr = JSONArray(allergiesJson)
    (0 until arr.length()).any { arr.getString(it).lowercase() in setOf("vegetarian", "vegan") }
}.getOrDefault(false)

private fun UserProfile.scheduleInput(target: DayTarget, carryOverKcal: Int) = ScheduleInput(
    goal = goal,
    weightKg = weightKg,
    targetKcal = target.kcal + carryOverKcal,
    targetProteinG = target.proteinG,
    // Carry-over kcal is split like the rest of the day: carbs and fat move, protein never does.
    targetCarbsG = target.carbsG + (carryOverKcal * 0.6 / 4).toInt(),
    targetFatG = target.fatG + (carryOverKcal * 0.4 / 9).toInt(),
    mealsPerDay = mealsPerDay,
    wakeTime = minutesToTime(wakeTimeMinutes),
    sleepTime = minutesToTime(sleepTimeMinutes),
    workoutTime = if (workoutEnabled) minutesToTime(workoutTimeMinutes) else null,
    template = if (isEarlyHeavyCut) ScheduleTemplate.FRONT_LOADED else ScheduleTemplate.BALANCED,
    preSleepProtein = goal == "Bulk",
    vegetarian = isVegetarian()
)

private fun MacroLog.localTime(): LocalTime =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()

/** A meal eaten after midnight but before the day boundary sorts at the end of the day. */
private fun MacroLog.toLoggedMeal(dayEndsAtHour: Int): LoggedMeal {
    val t = localTime()
    return LoggedMeal(
        time = if (t.hour < dayEndsAtHour) LocalTime.of(23, 59) else t,
        slot = mealSlot?.let { runCatching { MealSlotType.valueOf(it) }.getOrNull() },
        kcal = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
        id = id
    )
}
