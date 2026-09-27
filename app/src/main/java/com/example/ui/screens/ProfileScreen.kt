package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OrderHistory
import com.example.ui.theme.*
import com.example.ui.viewmodel.DietaryLogViewModel
import com.example.ui.viewmodel.MacroMateViewModel
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun ProfileScreen(
    viewModel: MacroMateViewModel,
    dietVm: DietaryLogViewModel,
) {
    val profile by viewModel.userProfile.collectAsState()
    val orders by viewModel.orderHistory.collectAsState()
    val context = LocalContext.current

    // Edit modes
    var isEditingProfile by remember { mutableStateOf(value = false) }
    var editName by remember { mutableStateOf("") }
    var editWeight by remember { mutableStateOf("") }
    var editGoal by remember { mutableStateOf("Maintain") }

    LaunchedEffect(profile) {
        profile?.let {
            editName = it.name
            editWeight = it.weightKg.toString()
            editGoal = it.goal
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        // Profile Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateCard)
                .padding(vertical = 16.dp, horizontal = 20.dp)
                .statusBarsPadding()
        ) {
            Column {
                Text(
                    text = "Profile Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Configure targets, providers, and view historical metrics",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: User Profile Settings Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(SlateDark, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "",
                                        tint = NeonEmerald
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = profile?.name ?: "Explorer",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Goal: ${profile?.goal} (${profile?.dailyCalories} kcal)",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isEditingProfile) {
                                        // Save
                                        val w = editWeight.toDoubleOrNull() ?: 70.0
                                        viewModel.saveProfile(
                                            name = editName,
                                            age = profile?.age ?: 25,
                                            weight = w,
                                            height = profile?.heightCm ?: 175.0,
                                            goal = editGoal,
                                            allergies = parseJsonList(profile?.allergiesJson ?: "[]"),
                                            cuisinePrefs = parseJsonList(profile?.cuisinePrefsJson ?: "[]")
                                        )
                                        Toast.makeText(context, "Profile parameters updated!", Toast.LENGTH_SHORT).show()
                                    }
                                    isEditingProfile = !isEditingProfile
                                }
                            ) {
                                Icon(
                                    imageVector = if (isEditingProfile) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = NeonEmerald
                                )
                            }
                        }

                        if (isEditingProfile) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = editWeight,
                                    onValueChange = { editWeight = it },
                                    label = { Text("Weight (kg)") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = SlateDark,
                                        unfocusedContainerColor = SlateDark,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                // Goal selector dropdown emulation
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                                        .background(SlateDark)
                                        .clickable {
                                            editGoal = when (editGoal) {
                                                "Cut" -> "Maintain"
                                                "Maintain" -> "Bulk"
                                                else -> "Cut"
                                            }
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Column {
                                        Text("Goal selector", fontSize = 10.sp, color = TextHint)
                                        Text(editGoal, fontSize = 14.sp, color = NeonEmerald, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section: BMI & BMR from the height, weight and age collected at onboarding
            item {
                BodyMetricsSection(dietVm = dietVm)
            }

            // Section 2: Monthly food budget
            item {
                MonthlyBudgetSection(dietVm = dietVm)
            }

            // Section: Swiggy MCP Live Connection
            item {
                val swiggyLinked by viewModel.swiggyLinked.collectAsState()
                val swiggyStatus by viewModel.swiggyStatus.collectAsState()
                val activeAddress by viewModel.activeSwiggyAddress.collectAsState()
                val isLocating by viewModel.isLocating.collectAsState()
                val locationDraft by viewModel.currentLocationDraft.collectAsState()
                var showAddressPicker by remember { mutableStateOf(false) }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    if (grants.values.any { it }) {
                        viewModel.resolveCurrentLocation()
                    } else {
                        Toast.makeText(context, "Location permission is needed to use your current location.", Toast.LENGTH_SHORT).show()
                    }
                }
                val requestCurrentLocation = {
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SWIGGY LIVE ORDERING",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = OrangeSwiggy,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (swiggyLinked)
                                "Connected — the concierge searches real Swiggy restaurants near your saved address."
                            else
                                "Not connected — using offline demo restaurants. Link your Swiggy account to search real menus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        swiggyStatus?.let { status ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (swiggyLinked) NeonEmerald else OrangeSwiggy
                            )
                        }

                        if (swiggyLinked) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateDark)
                                    .clickable {
                                        viewModel.loadSwiggyAddresses()
                                        showAddressPicker = true
                                    }
                                    .testTag("swiggy_address_row")
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Delivering to", fontSize = 10.sp, color = TextHint)
                                    Text(
                                        text = activeAddress ?: "Choose an address",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                }
                                Text("Change", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = requestCurrentLocation,
                                enabled = !isLocating,
                                border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("swiggy_current_location_button")
                            ) {
                                if (isLocating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonEmerald, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Finding your location…", color = NeonEmerald, fontSize = 13.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = NeonEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Use my current location", color = NeonEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (swiggyLinked) viewModel.disconnectSwiggy() else viewModel.connectSwiggy()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("swiggy_connect_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (swiggyLinked) SlateDark else OrangeSwiggy
                            )
                        ) {
                            Text(
                                text = if (swiggyLinked) "Disconnect Swiggy" else "Connect Swiggy Account",
                                fontWeight = FontWeight.Bold,
                                color = if (swiggyLinked) TextPrimary else Color.White
                            )
                        }
                    }
                }

                if (showAddressPicker) {
                    SwiggyAddressPickerDialog(
                        viewModel = viewModel,
                        onUseCurrentLocation = {
                            showAddressPicker = false
                            requestCurrentLocation()
                        },
                        onDismiss = { showAddressPicker = false }
                    )
                }

                locationDraft?.let { draft ->
                    CurrentLocationConfirmDialog(
                        draft = draft,
                        saving = isLocating,
                        onConfirm = viewModel::saveCurrentLocationToSwiggy,
                        onDismiss = viewModel::dismissCurrentLocationDraft
                    )
                }
            }

            // Section: on-device Qwen model status / download
            item { QwenModelCard(viewModel) }

            // Section: Budget & Meal Timing Settings

            // Section 3: Past Orders with Breakdown
            item {
                Text(
                    text = "Past Orders Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (orders.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No past orders logged yet.",
                            color = TextHint,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(orders) { order ->
                    OrderHistoryItemRow(order)
                }
            }

            // Section 4: Logout and Clear Data Actions
            item {
                Spacer(modifier = Modifier.height(16.dp))

                val haptic = LocalHapticFeedback.current

                // Sign Out Button with spring scale
                val signOutInteractionSource = remember { MutableInteractionSource() }
                val isSignOutPressed by signOutInteractionSource.collectIsPressedAsState()
                val signOutScale by animateFloatAsState(
                    targetValue = if (isSignOutPressed) 0.97f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "signOutScale"
                )

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.logout()
                        Toast.makeText(context, "Logged out successfully!", Toast.LENGTH_SHORT).show()
                    },
                    interactionSource = signOutInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .graphicsLayer(scaleX = signOutScale, scaleY = signOutScale)
                        .testTag("profile_logout_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeSwiggy),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out of Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clear Data Button with spring scale
                val clearDataInteractionSource = remember { MutableInteractionSource() }
                val isClearDataPressed by clearDataInteractionSource.collectIsPressedAsState()
                val clearDataScale by animateFloatAsState(
                    targetValue = if (isClearDataPressed) 0.97f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "clearDataScale"
                )

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.resetAllData()
                        Toast.makeText(context, "All user cache and details cleared!", Toast.LENGTH_SHORT).show()
                    },
                    interactionSource = clearDataInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .graphicsLayer(scaleX = clearDataScale, scaleY = clearDataScale)
                        .testTag("submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed.copy(alpha = 0.1f), contentColor = SoftRed),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Reset"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Local Data & Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SwiggyAddressPickerDialog(
    viewModel: MacroMateViewModel,
    onUseCurrentLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    val addresses by viewModel.swiggyAddresses.collectAsState()
    val loading by viewModel.swiggyAddressesLoading.collectAsState()
    val hasMore by viewModel.swiggyAddressesHasMore.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        title = { Text("Choose delivery address", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateDark)
                        .clickable(onClick = onUseCurrentLocation)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Use my current location", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NeonEmerald)
                        Text("Detect via GPS and save it to Swiggy", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("SAVED ON SWIGGY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextHint, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                if (addresses.isEmpty() && loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonEmerald)
                    }
                } else if (addresses.isEmpty()) {
                    Text(
                        "No saved addresses found on this Swiggy account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(addresses, key = { it.id }) { address ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.selectSwiggyAddress(address)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp)
                            ) {
                                Column {
                                    Text(address.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                    if (address.line.isNotBlank()) {
                                        Text(address.line, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                                    }
                                }
                            }
                        }
                        if (hasMore) {
                            item {
                                TextButton(
                                    onClick = { viewModel.loadSwiggyAddresses(reset = false) },
                                    enabled = !loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (loading) "Loading…" else "Load more", color = NeonEmerald)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

/**
 * Review step before the detected location is saved as a real Swiggy address — reverse
 * geocoding is approximate, and the flat/floor line can never be detected from GPS.
 */
@Composable
private fun CurrentLocationConfirmDialog(
    draft: MacroMateViewModel.CurrentLocationDraft,
    saving: Boolean,
    onConfirm: (MacroMateViewModel.CurrentLocationDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val a = draft.address
    var flat by remember(draft) { mutableStateOf(a.addressLine2) }
    var street by remember(draft) { mutableStateOf(a.addressLine) }
    var city by remember(draft) { mutableStateOf(a.city) }
    var pincode by remember(draft) { mutableStateOf(a.postalCode) }
    var name by remember(draft) { mutableStateOf(draft.userName) }
    var phone by remember(draft) { mutableStateOf(draft.userPhone) }

    val valid = street.isNotBlank() && city.isNotBlank() && pincode.isNotBlank() &&
            name.isNotBlank() && phone.isNotBlank()

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = SlateDark,
        unfocusedContainerColor = SlateDark,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = SlateCard,
        title = { Text("Confirm current location", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Detected at %.5f, %.5f. Check the details — this is saved to your Swiggy account as \"MacroMate · Current location\" and replaces the one MacroMate saved before.".format(a.latitude, a.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = flat, onValueChange = { flat = it },
                    label = { Text("Flat / floor / building (optional)") },
                    singleLine = true, colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("current_location_flat_input")
                )
                OutlinedTextField(
                    value = street, onValueChange = { street = it },
                    label = { Text("Street / area") },
                    singleLine = true, colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city, onValueChange = { city = it },
                        label = { Text("City") },
                        singleLine = true, colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pincode, onValueChange = { pincode = it },
                        label = { Text("Pincode") },
                        singleLine = true, colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true, colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Swiggy phone number") },
                    singleLine = true, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val full = listOf(flat, street, a.locality, city, pincode)
                        .map(String::trim).filter(String::isNotEmpty).distinct().joinToString(", ")
                    onConfirm(
                        draft.copy(
                            address = a.copy(
                                fullAddress = full,
                                addressLine = street.trim(),
                                addressLine2 = flat.trim(),
                                city = city.trim(),
                                postalCode = pincode.trim()
                            ),
                            userName = name.trim(),
                            userPhone = phone.trim()
                        )
                    )
                },
                enabled = valid && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                modifier = Modifier.testTag("current_location_confirm_button")
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Deliver here")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel", color = TextSecondary) }
        }
    )
}

// Order History Item UI view list components
@Composable
fun OrderHistoryItemRow(order: OrderHistory) {
    val formatter = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
    val dateStr = formatter.format(Date(order.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ID: ${order.orderId}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Text(
                    text = "₹${order.totalPrice.toInt()}",
                    fontWeight = FontWeight.Black,
                    color = OrangeSwiggy,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Parse json items
            val rawItems = parseJsonList(order.itemsJson)
            rawItems.forEach { itemDesc ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "",
                        tint = NeonEmerald,
                        modifier = Modifier.size(6.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = itemDesc,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Macros row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SlateDark)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(
                    text = "P: ${order.totalProtein}g",
                    fontSize = 11.sp,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "C: ${order.totalCarbs}g",
                    fontSize = 11.sp,
                    color = ElectricIndigo,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "F: ${order.totalFat}g",
                    fontSize = 11.sp,
                    color = GoldenAmber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${order.totalCalories} kcal",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun parseJsonList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val list = mutableListOf<String>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

/** Monthly food budget editor. 0 or blank switches the budget off. */
@Composable
private fun MonthlyBudgetSection(dietVm: DietaryLogViewModel) {
    val dietProfile by dietVm.profile.collectAsState()
    val context = LocalContext.current
    val saved = dietProfile?.monthlyBudgetInr ?: 0.0
    var budgetText by remember { mutableStateOf("") }

    // Prefill from the stored budget (blank when none is set).
    LaunchedEffect(saved) {
        budgetText = if (saved > 0.0) saved.toLong().toString() else ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_budget_section"),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly food budget",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 16.sp
            )
            Text(
                text = "Tracks Swiggy orders and outside spend against a monthly limit",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { input -> budgetText = input.filter { it.isDigit() }.take(7) },
                    prefix = { Text("₹", color = OrangeSwiggy, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("e.g. 6000", color = TextHint) },
                    supportingText = { Text("0 or empty turns the budget off", fontSize = 11.sp, color = TextHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SlateDark,
                        unfocusedContainerColor = SlateDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("monthly_budget_input")
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val value = budgetText.toDoubleOrNull() ?: 0.0
                        dietVm.setMonthlyBudget(value)
                        Toast.makeText(
                            context,
                            if (value > 0) "Monthly budget saved" else "Monthly budget turned off",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    shape = RoundedCornerShape(10.dp),
                    // Keeps the button aligned with the text field rather than its helper text.
                    modifier = Modifier
                        .padding(bottom = 22.dp)
                        .testTag("save_budget_button")
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BodyMetricsSection(dietVm: DietaryLogViewModel) {
    val p by dietVm.profile.collectAsState()
    val profile = p ?: return
    val bmi = com.example.domain.BodyMetrics.bmi(profile.weightKg, profile.heightCm) ?: return
    val adult = com.example.domain.BodyMetrics.bmiCategoryApplies(profile.age)
    val category = com.example.domain.BodyMetrics.bmiCategory(bmi)
    val healthy = com.example.domain.BodyMetrics.healthyWeightRangeKg(profile.heightCm)
    val bmrExact = com.example.domain.BodyMetrics.bmrFor(profile.weightKg, profile.heightCm, profile.age, profile.sex)
    val bmr = com.example.domain.BodyMetrics.bmrRange(profile.weightKg, profile.heightCm, profile.age)
    val categoryColor = when (category) {
        com.example.domain.BmiCategory.HEALTHY -> NeonEmerald
        com.example.domain.BmiCategory.UNDERWEIGHT, com.example.domain.BmiCategory.OVERWEIGHT -> GoldenAmber
        com.example.domain.BmiCategory.OBESE -> SoftRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("body_metrics_section"),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Body metrics", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            Text(
                "From your height (${profile.heightCm.toInt()} cm), weight (${"%.1f".format(profile.weightKg)} kg) and age (${profile.age})",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // BMI
                Column(modifier = Modifier.weight(1f)) {
                    Text("BMI", fontSize = 11.sp, color = TextHint)
                    Text("%.1f".format(bmi), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (adult) {
                        Text(category.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = categoryColor)
                    } else {
                        Text("Adult ranges don't apply under 18", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                // BMR
                Column(modifier = Modifier.weight(1f)) {
                    Text("BMR (at rest)", fontSize = 11.sp, color = TextHint)
                    if (bmrExact != null) {
                        Text("%,d".format(bmrExact), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("kcal/day", fontSize = 12.sp, color = TextSecondary)
                    } else if (bmr != null) {
                        Text(
                            "%,d–%,d".format(bmr.first, bmr.last),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text("kcal/day · pick gender below", fontSize = 12.sp, color = TextSecondary)
                    } else {
                        Text("—", fontSize = 24.sp, color = TextHint)
                    }
                }
            }
            if (adult) {
                Spacer(modifier = Modifier.height(12.dp))
                BmiScale(bmi = bmi)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gender", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                listOf("Male", "Female").forEach { option ->
                    val selected = profile.sex == option
                    FilterChip(
                        selected = selected,
                        onClick = { if (!selected) dietVm.setSex(option) },
                        label = { Text(option) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonEmerald,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("profile_sex_$option")
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (adult && healthy != null) {
                Text(
                    "Healthy weight for your height: ${healthy.first}–${healthy.second} kg (BMI 18.5–24.9).",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Text(
                "BMR is the energy your body uses at complete rest (Mifflin-St Jeor). BMI can overstate " +
                    "risk for very muscular people. Both are estimates, not a diagnosis.",
                fontSize = 11.sp,
                color = TextHint
            )
        }
    }
}

/** WHO bands 16–35 with a marker at the user's BMI. */
@Composable
private fun BmiScale(bmi: Double) {
    val min = 16.0
    val max = 35.0
    val bands = listOf(18.5 to GoldenAmber, 25.0 to NeonEmerald, 30.0 to GoldenAmber, max to SoftRed)
    Column {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val barTop = size.height * 0.35f
            val barHeight = size.height * 0.4f
            var start = min
            bands.forEach { (end, color) ->
                val x0 = ((start - min) / (max - min)).toFloat() * size.width
                val x1 = ((end - min) / (max - min)).toFloat() * size.width
                drawRect(color.copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(x0, barTop),
                    size = androidx.compose.ui.geometry.Size(x1 - x0, barHeight))
                start = end
            }
            val x = ((bmi.coerceIn(min, max) - min) / (max - min)).toFloat() * size.width
            drawCircle(TextPrimary, radius = size.height * 0.4f, center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
            drawCircle(SlateCard, radius = size.height * 0.2f, center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Under", "Healthy", "Over", "Obese").forEach {
                Text(it, fontSize = 10.sp, color = TextHint)
            }
        }
    }
}
