package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.domain.schedule.MealSlot
import com.example.domain.schedule.ScheduleTemplate
import com.example.ui.theme.LightTeal
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateDark
import com.example.ui.theme.SoftRed
import com.example.ui.theme.TextHint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MacroMateViewModel
import com.example.ui.viewmodel.scheduleTemplate
import com.example.ui.viewmodel.sleepTime
import com.example.ui.viewmodel.wakeTime
import com.example.ui.viewmodel.workoutTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
private fun dlFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SlateDark,
    unfocusedContainerColor = SlateDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

// ---------------------------------------------------------------------------------------------
// Log a meal into a slot
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogMealDialog(
    viewModel: MacroMateViewModel,
    slot: MealSlot,
    onDismiss: () -> Unit
) {
    // Prefill with what's left for this slot, so a quick "Log it" is one tap for a planned meal.
    val remainingKcal = (slot.targetKcal - slot.loggedKcal).coerceAtLeast(0)
    val remainingProtein = (slot.targetProteinG - slot.loggedProteinG).coerceAtLeast(0)
    val share = if (slot.targetKcal > 0) remainingKcal.toDouble() / slot.targetKcal else 0.0

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf(remainingProtein.toString()) }
    var carbs by remember { mutableStateOf((slot.targetCarbsG * share).toInt().toString()) }
    var fat by remember { mutableStateOf((slot.targetFatG * share).toInt().toString()) }
    var kcal by remember { mutableStateOf(remainingKcal.toString()) }
    var cost by remember { mutableStateOf("") }
    var estimateError by remember { mutableStateOf<String?>(null) }
    val isEstimating by viewModel.isEstimatingMacros.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Log ${slot.type.label.lowercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("What did you eat?") },
                    singleLine = true,
                    colors = dlFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; estimateError = null },
                    label = { Text("Describe it (optional, e.g. 2 rotis and dal)") },
                    colors = dlFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        estimateError = null
                        viewModel.estimateMacrosFromDescription(description) { result ->
                            if (result != null) {
                                kcal = result.calories.toString()
                                protein = result.protein.toString()
                                carbs = result.carbs.toString()
                                fat = result.fat.toString()
                                if (name.isBlank()) name = description
                            } else {
                                estimateError = "Couldn't estimate. Enter the numbers below instead."
                            }
                        }
                    },
                    enabled = !isEstimating && description.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    if (isEstimating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonEmerald, strokeWidth = 2.dp)
                    } else {
                        Text("Estimate with AI", color = NeonEmerald)
                    }
                }
                estimateError?.let { Text(it, color = SoftRed, style = MaterialTheme.typography.bodySmall) }

                HorizontalDivider(color = SlateBorder)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Protein (g)", protein, { protein = it }, Modifier.weight(1f))
                    NumberField("Carbs (g)", carbs, { carbs = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Fat (g)", fat, { fat = it }, Modifier.weight(1f))
                    NumberField("Energy (kcal)", kcal, { kcal = it }, Modifier.weight(1f))
                }
                NumberField("Cost (₹, optional)", cost, { cost = it }, Modifier.fillMaxWidth(), decimal = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.logManualMeal(
                        name = name.ifBlank { description.ifBlank { slot.type.label } },
                        company = "",
                        protein = protein.toIntOrNull() ?: 0,
                        carbs = carbs.toIntOrNull() ?: 0,
                        fat = fat.toIntOrNull() ?: 0,
                        calories = kcal.toIntOrNull() ?: 0,
                        cost = cost.toDoubleOrNull() ?: 0.0,
                        mealSlot = slot.type.name
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
            ) { Text("Log meal") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = SlateCard,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    decimal: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') }.take(7)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = dlFieldColors(),
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------------------------
// Outside spend
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DietaryLogOutsideSpendDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (amountInr: Double, note: String?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Food spend on ${fmtDayShort(date)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Anything you paid for food outside the app, so your budget stays accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                NumberField("Amount (₹)", amount, { amount = it }, Modifier.fillMaxWidth(), decimal = true)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    colors = dlFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let { onSave(it, note.takeIf { n -> n.isNotBlank() }) } },
                enabled = parsed != null && parsed > 0,
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = SlateCard,
        shape = RoundedCornerShape(16.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// Schedule settings sheet
// ---------------------------------------------------------------------------------------------

private val SETTINGS_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun parseSettingsTime(value: String?, fallback: LocalTime): LocalTime =
    value?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback

private enum class TimeField { WAKE, SLEEP, WORKOUT }

@Composable
private fun HealthSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = NeonEmerald)
        )
    }
}

/** Month-end reconcile: the user enters what they really spent on food this month. */
@Composable
internal fun DietaryLogReconcileDialog(knownSpend: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = text.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        title = { Text("Reconcile this month", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "MacroMate has ₹${java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(knownSpend.toLong())} " +
                        "on record. Enter what you actually spent on food this month (from your bank or UPI app) " +
                        "and we'll add the difference as one adjustment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(7) },
                    label = { Text("Actual spend") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = { amount?.let(onSave) }) {
                Text("Save", color = NeonEmerald)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DietaryLogScheduleSheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (
        mealsPerDay: Int, wakeTime: String, sleepTime: String, workoutTime: String?,
        template: ScheduleTemplate, dayEndsAtHour: Int, remindersEnabled: Boolean,
        pregnantOrBreastfeeding: Boolean, onDiabetesMeds: Boolean, edHistory: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var meals by remember { mutableIntStateOf(profile.mealsPerDay.coerceIn(2, 6)) }
    var wake by remember { mutableStateOf(parseSettingsTime(profile.wakeTime, LocalTime.of(7, 0))) }
    var sleep by remember { mutableStateOf(parseSettingsTime(profile.sleepTime, LocalTime.of(23, 0))) }
    var workout by remember {
        mutableStateOf(profile.workoutTime?.let { parseSettingsTime(it, LocalTime.of(18, 0)) })
    }
    var template by remember {
        mutableStateOf(runCatching { ScheduleTemplate.valueOf(profile.scheduleTemplate) }.getOrDefault(ScheduleTemplate.BALANCED))
    }
    var dayEnds by remember { mutableIntStateOf(profile.dayEndsAtHour.coerceIn(0, 6)) }
    var reminders by remember { mutableStateOf(profile.remindersEnabled) }
    var pregnant by remember { mutableStateOf(profile.isPregnantOrLactating) }
    var diabetesMeds by remember { mutableStateOf(profile.onDiabetesMeds) }
    var edHistory by remember { mutableStateOf(profile.edHistory) }
    var editing by remember { mutableStateOf<TimeField?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text("Meal schedule", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                "Shapes your daily timeline. Meal count is personal preference; it doesn't change results.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            SettingsLabel("Meals per day")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                (2..6).forEachIndexed { index, n ->
                    SegmentedButton(
                        selected = meals == n,
                        onClick = { meals = n },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 5),
                        colors = segmentedColors()
                    ) { Text(n.toString()) }
                }
            }

            SettingsLabel("Your day")
            TimeRow("Wake up", wake.format(SETTINGS_TIME)) { editing = TimeField.WAKE }
            HorizontalDivider(color = SlateBorder)
            TimeRow("Sleep", sleep.format(SETTINGS_TIME)) { editing = TimeField.SLEEP }
            HorizontalDivider(color = SlateBorder)
            TimeRow("Workout", workout?.format(SETTINGS_TIME) ?: "No workout") { editing = TimeField.WORKOUT }
            if (workout != null) {
                TextButton(onClick = { workout = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("No workout today", color = TextSecondary)
                }
            }

            SettingsLabel("Plan style")
            TemplateOption(
                title = "Balanced",
                body = "Energy spread evenly across your meals.",
                selected = template == ScheduleTemplate.BALANCED
            ) { template = ScheduleTemplate.BALANCED }
            TemplateOption(
                title = "Front-loaded",
                body = "Bigger breakfast, less hunger; same fat loss.",
                selected = template == ScheduleTemplate.FRONT_LOADED
            ) { template = ScheduleTemplate.FRONT_LOADED }

            SettingsLabel("Day ends at")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                (0..6).forEachIndexed { index, h ->
                    SegmentedButton(
                        selected = dayEnds == h,
                        onClick = { dayEnds = h },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 7),
                        colors = segmentedColors(),
                        icon = {}
                    ) { Text(h.toString()) }
                }
            }
            Text(
                "${if (dayEnds == 0) "Midnight" else "$dayEnds:00 AM"}. Meals before this time count toward the previous day.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Meal reminders", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text("A gentle nudge around each planned meal", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = reminders,
                    onCheckedChange = { reminders = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = NeonEmerald)
                )
            }

            SettingsLabel("Health check (optional)")
            Text(
                "If any of these apply, MacroMate pauses calorie carry-over and fasting suggestions. " +
                    "Plan fasts and calorie cuts with your doctor.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            HealthSwitch("Pregnant or breastfeeding", pregnant) { pregnant = it }
            HealthSwitch("Taking insulin or other diabetes medication", diabetesMeds) { diabetesMeds = it }
            HealthSwitch("History of an eating disorder", edHistory) { edHistory = it }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onSave(
                        meals, wake.format(SETTINGS_TIME), sleep.format(SETTINGS_TIME),
                        workout?.format(SETTINGS_TIME), template, dayEnds, reminders,
                        pregnant, diabetesMeds, edHistory
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Save schedule") }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editing?.let { field ->
        val initial = when (field) {
            TimeField.WAKE -> wake
            TimeField.SLEEP -> sleep
            TimeField.WORKOUT -> workout ?: LocalTime.of(18, 0)
        }
        DlTimePickerDialog(
            title = when (field) {
                TimeField.WAKE -> "Wake-up time"
                TimeField.SLEEP -> "Bedtime"
                TimeField.WORKOUT -> "Workout time"
            },
            initial = initial,
            onDismiss = { editing = null },
            onConfirm = { t ->
                when (field) {
                    TimeField.WAKE -> wake = t
                    TimeField.SLEEP -> sleep = t
                    TimeField.WORKOUT -> workout = t
                }
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun segmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = LightTeal,
    activeContentColor = NeonEmerald,
    activeBorderColor = SlateBorder,
    inactiveContainerColor = SlateCard,
    inactiveContentColor = TextSecondary,
    inactiveBorderColor = SlateBorder
)

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Change $label time", onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NeonEmerald)
    }
}

@Composable
private fun TemplateOption(title: String, body: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onSelect)
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = NeonEmerald, unselectedColor = TextHint)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DlTimePickerDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    selectorColor = NeonEmerald,
                    timeSelectorSelectedContainerColor = LightTeal,
                    timeSelectorSelectedContentColor = NeonEmerald,
                    clockDialColor = SlateDark,
                    clockDialSelectedContentColor = Color.White
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("OK", color = NeonEmerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = SlateCard
    )
}
