package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.BudgetStatus
import com.example.data.repository.ScheduledMealSlot
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel

@Composable
fun DietaryLogScreen(
    viewModel: MacroMateViewModel,
    onNavigateToChat: (prefilledQuery: String) -> Unit = {}
) {
    val schedulePlan by viewModel.dailySchedulePlan.collectAsState()
    val budgetStatus by viewModel.budgetStatus.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val todayLogs by viewModel.todayLogs.collectAsState()

    var showOutsideSpendDialog by remember { mutableStateOf(false) }
    var outsideAmountText by remember { mutableStateOf("") }
    var outsideNoteText by remember { mutableStateOf("") }

    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInputText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Screen Header with Full-Color Icon ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_dietary_log_full),
                        contentDescription = "Dietary Schedule Icon",
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dietary Schedule & Log",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = schedulePlan.goalGuideline,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // --- 2. Monthly Food Budget & Daily Allowance Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Monthly Food Budget",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        IconButton(
                            onClick = { showOutsideSpendDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "Add outside spend",
                                tint = NeonEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (budgetStatus.monthlyBudgetInr <= 0.0) {
                        Text(
                            text = "No monthly budget cap set. You can set a budget under Profile to track spending and receive safe daily allowances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else {
                        val progress = if (budgetStatus.monthlyBudgetInr > 0) {
                            (budgetStatus.currentMonthSpendInr / budgetStatus.monthlyBudgetInr).toFloat().coerceIn(0f, 1f)
                        } else 0f

                        val barColor = when {
                            budgetStatus.isBudgetExhausted -> SoftRed
                            progress > 0.8f -> OrangeSwiggy
                            else -> NeonEmerald
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = barColor,
                            trackColor = SlateDark
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Spent: ₹${budgetStatus.currentMonthSpendInr.toInt()} / ₹${budgetStatus.monthlyBudgetInr.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Remaining: ₹${budgetStatus.remainingBudgetInr.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (budgetStatus.isBudgetExhausted) SoftRed else NeonEmerald
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (budgetStatus.isBudgetExhausted) {
                                "⚠️ Budget used up for this month."
                            } else {
                                "Safe allowance: ₹${budgetStatus.safeDailyAllowanceInr?.toInt() ?: 0} / day (${budgetStatus.daysRemainingInMonth} days left)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (budgetStatus.isBudgetExhausted) SoftRed else TextSecondary
                        )
                    }
                }
            }
        }

        // --- 3. Body Weight Quick Check-In ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Body Weight Check-in",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Current: ${userProfile?.weightKg ?: 70.0} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Button(
                        onClick = {
                            weightInputText = "${userProfile?.weightKg ?: 70.0}"
                            showWeightDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Log Weight", color = NeonEmerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 4. Scheduled Meal Cadence Header ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Meal Cadence (${schedulePlan.slots.size} Meals)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${schedulePlan.totalCalories} kcal · ${schedulePlan.totalProteinG}g P",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonEmerald
                )
            }
        }

        // --- 5. Scheduled Meal Slots ---
        items(schedulePlan.slots) { slot ->
            ScheduledMealCard(
                slot = slot,
                onOrderSwiggy = {
                    val prompt = when {
                        slot.isPostWorkout -> "Find a high protein post-workout recovery meal under 250 rs"
                        slot.isBedtimeSlowProtein -> "Find a slow protein paneer or curd meal for bedtime"
                        else -> "Find a balanced meal for ${slot.name} with ${slot.targetProteinG}g protein"
                    }
                    onNavigateToChat(prompt)
                }
            )
        }
    }

    // --- Outside Spend Dialog ---
    if (showOutsideSpendDialog) {
        AlertDialog(
            onDismissRequest = { showOutsideSpendDialog = false },
            title = { Text("Log Outside Food Spend") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Record food purchases made outside Swiggy (street stalls, grocery, dining out):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = outsideAmountText,
                        onValueChange = { outsideAmountText = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = outsideNoteText,
                        onValueChange = { outsideNoteText = it },
                        label = { Text("Note (e.g. Juice stall)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = outsideAmountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.recordOutsideSpend(amt, outsideNoteText)
                        }
                        showOutsideSpendDialog = false
                        outsideAmountText = ""
                        outsideNoteText = ""
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showOutsideSpendDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- Weight Log Dialog ---
    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("Log Body Weight") },
            text = {
                OutlinedTextField(
                    value = weightInputText,
                    onValueChange = { weightInputText = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val w = weightInputText.toDoubleOrNull() ?: 70.0
                        viewModel.recordWeight(w)
                        showWeightDialog = false
                    }
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ScheduledMealCard(
    slot: ScheduledMealSlot,
    onOrderSwiggy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (slot.isPostWorkout) OrangeSwiggy else NeonEmerald)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = slot.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${slot.timeFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                if (slot.isPostWorkout) {
                    Badge(containerColor = OrangeSwiggy.copy(alpha = 0.2f)) {
                        Text("POST-WORKOUT", color = OrangeSwiggy, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                } else if (slot.isBedtimeSlowProtein) {
                    Badge(containerColor = NeonEmerald.copy(alpha = 0.2f)) {
                        Text("BEDTIME CASEIN", color = NeonEmerald, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Macro targets pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🔥 ${slot.targetCalories} kcal", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("🥩 ${slot.targetProteinG}g Protein", style = MaterialTheme.typography.bodySmall, color = NeonEmerald, fontWeight = FontWeight.SemiBold)
                Text("🍚 ${slot.targetCarbsG}g C", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("🥑 ${slot.targetFatG}g F", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = slot.suggestedFoodTypes,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onOrderSwiggy,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeSwiggy.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeliveryDining,
                    contentDescription = null,
                    tint = OrangeSwiggy,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Find fitting Swiggy meal",
                    color = OrangeSwiggy,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
