package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: MacroMateViewModel,
    onOnboardingComplete: () -> Unit,
) {
    var step by remember { mutableIntStateOf(1) }

    // Form states (name pre-filled from the account created at signup)
    var name by remember { mutableStateOf(viewModel.userProfile.value?.name ?: "") }
    var ageStr by remember { mutableStateOf("25") }
    var weightStr by remember { mutableStateOf("70") }
    var heightStr by remember { mutableStateOf("175") }

    var selectedGoal by remember { mutableStateOf("Maintain") }

    val dietaryRestrictions = remember {
        mutableStateListOf("vegetarian", "vegan", "gluten-free", "nut allergy", "dairy-free").map {
            mutableStateOf(it to false)
        }
    }

    val cuisinePreferences = remember {
        mutableStateListOf("North Indian", "South Indian", "Continental", "Keto", "High Protein", "Salads").map {
            mutableStateOf(it to false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = SlateDark)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Logo & Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestaurantMenu,
                    contentDescription = "MacroMate",
                    tint = OrangeSwiggy,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "MacroMate",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "AI-POWERED NUTRITIONAL CONCIERGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangeSwiggy,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
            }

            // Steps Progress Bar Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                for (i in 1..4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                color = if (i <= step) NeonEmerald else SlateBorder
                            )
                    )
                }
            }

            // Central Dynamic Form Card Block
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 2 } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> -width / 2 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width / 2 } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> width / 2 } + fadeOut())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "OnboardingStepTransition"
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (currentStep) {
                        1 -> {
                            Text(
                                text = "Tell us about yourself",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "This allows our AI nutrition agent to calculate customized protein and metabolic structures for your needs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Your Name") },
                                leadingIcon = { Icon(Icons.Default.Person, "Name", tint = NeonEmerald) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .testTag("name_input"),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SlateCard,
                                    unfocusedContainerColor = SlateCard,
                                    focusedLabelColor = NeonEmerald,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = ageStr,
                                onValueChange = { ageStr = it },
                                label = { Text("Age (years)") },
                                leadingIcon = { Icon(Icons.Default.CalendarToday, "Age", tint = NeonEmerald) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SlateCard,
                                    unfocusedContainerColor = SlateCard,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = weightStr,
                                    onValueChange = { weightStr = it },
                                    label = { Text("Weight (kg)") },
                                    leadingIcon = { Icon(Icons.Default.Scale, "Weight", tint = NeonEmerald) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = SlateCard,
                                        unfocusedContainerColor = SlateCard,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                OutlinedTextField(
                                    value = heightStr,
                                    onValueChange = { heightStr = it },
                                    label = { Text("Height (cm)") },
                                    leadingIcon = { Icon(Icons.Default.Height, "Height", tint = NeonEmerald) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = SlateCard,
                                        unfocusedContainerColor = SlateCard,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                        }
                        2 -> {
                            Text(
                                text = "Select your priority goal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "We'll configure caloric and protein parameters accordingly to match daily metabolic target goals.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            listOf(
                                Triple(
                                    "Cut",
                                    "Fat Loss & Definition",
                                    "Restricted calorie envelope designed to safely oxidize lipids while highlighting muscle mass. Higher protein requirement."
                                ),
                                Triple(
                                    "Maintain",
                                    "Athletic Balance",
                                    "Optimal homeostatic equilibrium. Guarantees daily cellular replenishment, stable glycogen stores, and steady energy levels."
                                ),
                                Triple(
                                    "Bulk",
                                    "Strength & Anabolics",
                                    "Caloric surplus focused on clean macro alignment to maximize strength increases and accelerate muscle hypertrophy."
                                )
                            ).forEach { (goalName, subtitle, desc) ->
                                val isSelected = selectedGoal == goalName
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clickable { selectedGoal = goalName }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) NeonEmerald else SlateBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) SlateCard.copy(alpha = 0.8f) else SlateCard
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { selectedGoal = goalName },
                                                    colors = RadioButtonDefaults.colors(selectedColor = NeonEmerald)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = goalName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) NeonEmerald else TextPrimary
                                                )
                                            }
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OrangeSwiggy,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                        3 -> {
                            Text(
                                text = "Dietary Restrictions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Select any restrictions to program the AI concierge to filter out unsafe menu constituents automatically.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            // Clean custom list
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SlateCard)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    dietaryRestrictions.forEach { restrictionState ->
                                        val (label, checked) = restrictionState.value
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    restrictionState.value = label to !checked
                                                }
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = label.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (checked) NeonEmerald else TextPrimary,
                                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = {
                                                    restrictionState.value = label to !checked
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = NeonEmerald)
                                            )
                                        }
                                        HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                        4 -> {
                            Text(
                                text = "Cuisine Preferences",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "MacroMate custom scores Swiggy menus. Preferred cuisines will receive micro-ranking advantages in the ReAct Agent.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SlateCard)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Render 2x columns for preferences
                                    cuisinePreferences.chunked(2).forEach { pair ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            pair.forEach { prefState ->
                                                val (label, checked) = prefState.value
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            prefState.value = label to !checked
                                                        }
                                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = checked,
                                                        onCheckedChange = {
                                                            prefState.value = label to !checked
                                                        },
                                                        colors = CheckboxDefaults.colors(checkedColor = NeonEmerald)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (checked) NeonEmerald else TextPrimary,
                                                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Navigation Button Block (Bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text("Back", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        if (step < 4) {
                            // Validate input
                            if (step == 1 && name.isBlank()) {
                                // Add fallback default or simple block
                                name = "Nutrition Hero"
                            }
                            step++
                        } else {
                            // Formulate save structures
                            val w = weightStr.toDoubleOrNull() ?: 70.0
                            val h = heightStr.toDoubleOrNull() ?: 175.0
                            val a = ageStr.toIntOrNull() ?: 25

                            val selectedAllergiesList = dietaryRestrictions
                                .asSequence()
                                .filter { it.value.second }
                                .map { it.value.first }
                                .toList()

                            val selectedCuisineList = cuisinePreferences
                                .asSequence()
                                .filter { it.value.second }
                                .map { it.value.first }
                                .toList()

                            viewModel.saveProfile(
                                name = name,
                                age = a,
                                weight = w,
                                height = h,
                                goal = selectedGoal,
                                allergies = selectedAllergiesList,
                                cuisinePrefs = selectedCuisineList
                            )
                            onOnboardingComplete()
                        }
                    },
                    modifier = Modifier
                        .weight(if (step > 1) 2f else 1f)
                        .testTag("submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (step == 4) NeonEmerald else OrangeSwiggy,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (step == 4) "Save & Enter MacroMate" else "Continue",
                        fontWeight = FontWeight.Bold
                    )
                    if (step < 4) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
