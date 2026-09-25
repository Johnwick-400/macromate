package com.example.ui.screens

import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MacroLog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel
import kotlin.math.min

@Composable
fun TrackerScreen(
    viewModel: MacroMateViewModel,
) {
    val todayLogs by viewModel.todayLogs.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    // Keyed on the collected state so the arcs recompute when Room emits new logs
    val consumed = remember(todayLogs) { viewModel.getConsumedMacros() }
    val profile by viewModel.userProfile.collectAsState()

    // Dialog state for manual log
    var showManualDialog by remember { mutableStateOf(value = false) }

    val limitCals = profile?.dailyCalories ?: 2000
    val limitProtein = profile?.dailyProteinG ?: 130
    val limitCarbs = profile?.dailyCarbsG ?: 220
    val limitFat = profile?.dailyFatG ?: 65

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        // Tracker Screen Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateCard)
                .padding(vertical = 16.dp, horizontal = 20.dp)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Fuel & Nutrition",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Real-time calorie & metabolic feedback",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonEmerald)
                        .tactilePress { showManualDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Log",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manual Log", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Main Scrollable Dashboard
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Progress Rings Container
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CALORIC PROGRESS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Arc Drawing
                        val caloriesOver = consumed.calories > limitCals
                        val calorieOverBy = consumed.calories - limitCals
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(160.dp)
                        ) {
                            val targetPercent = if (limitCals > 0) consumed.calories.toFloat() / limitCals.toFloat() else 0f
                            val animatedPercent by animateFloatAsState(
                                targetValue = minOf(1f, targetPercent),
                                animationSpec = tween(1200, easing = FastOutSlowInEasing),
                                label = "ringProgress"
                            )
                            val strokeWidthVal = 14.dp
                            // Over target → ring goes full red; otherwise emerald progress
                            val ringColor = if (caloriesOver) SoftRed else NeonEmerald
                            val ringSweep = if (caloriesOver) 1f else animatedPercent

                            Canvas(modifier = Modifier.size(140.dp)) {
                                // Background circle ring
                                drawCircle(
                                    color = SlateBorder,
                                    style = Stroke(width = strokeWidthVal.toPx())
                                )
                                // Active progress Arc
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = ringSweep * 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidthVal.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = consumed.calories.toString(),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = if (caloriesOver) SoftRed else TextPrimary
                                )
                                Text(
                                    text = "of $limitCals kcal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (caloriesOver) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(SoftRed.copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "▲ $calorieOverBy kcal over",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SoftRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Flanking horizontal individual bars
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroBarItem(
                                title = "Protein",
                                current = consumed.protein,
                                limit = limitProtein,
                                color = NeonEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            MacroBarItem(
                                title = "Carbs",
                                current = consumed.carbs,
                                limit = limitCarbs,
                                color = ElectricIndigo,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            MacroBarItem(
                                title = "Fats",
                                current = consumed.fat,
                                limit = limitFat,
                                color = GoldenAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section 2: Remaining Macros tip — flips to a red over-target warning when exceeded
            item {
                val overCals = consumed.calories > limitCals
                val calsOverBy = consumed.calories - limitCals
                val carbsOverBy = consumed.carbs - limitCarbs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (overCals) SoftRed else NeonEmerald)
                    )
                    Row(
                        modifier = Modifier.padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (overCals) Icons.Default.Warning else Icons.AutoMirrored.Filled.DirectionsRun,
                            contentDescription = "",
                            tint = if (overCals) SoftRed else NeonEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            if (overCals) {
                                Text(
                                    text = "You're $calsOverBy kcal over today's target",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SoftRed,
                                    fontWeight = FontWeight.Bold
                                )
                                val extra = if (carbsOverBy > 0) " Carbs also ${carbsOverBy}g over." else ""
                                Text(
                                    text = "Consider a lighter next meal.$extra",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            } else {
                                val remProtein = Math.max(0, limitProtein - consumed.protein)
                                val remCals = Math.max(0, limitCals - consumed.calories)
                                Text(
                                    text = "${remProtein}g protein remaining today",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Your metabolic target has $remCals calories left for matching dynamic Swiggy finds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: 7-Day History Custom Chart
            item {
                val chartPoints = remember(allLogs) { viewModel.get7DayHistory() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "WEEKLY METRIC INTEGRATION",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Canvas Chart Block
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                Alignment.Bottom
                            ) {
                                val maxCals = chartPoints.maxOf { it.calories }.coerceAtLeast(1500)

                                chartPoints.forEachIndexed { index, point ->
                                    val targetRatio = (point.calories.toFloat() / maxCals.toFloat()).coerceAtLeast(0.08f)
                                    val animatedRatio by animateFloatAsState(
                                        targetValue = targetRatio,
                                        animationSpec = tween(
                                            durationMillis = 600,
                                            delayMillis = index * 60,
                                            easing = FastOutSlowInEasing
                                        ),
                                        label = "chartBar$index"
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Label value
                                        Text(
                                            text = if (point.calories > 0) "${point.calories}" else "-",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (point.calories > 0) NeonEmerald else TextHint
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Bar Canvas Block
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .fillMaxHeight(animatedRatio)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(
                                                    color = if (point.calories > 0) NeonEmerald else SlateBorder
                                                )
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Day label
                                        Text(
                                            text = point.dayLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Logged meal items
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${todayLogs.size} logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            if (todayLogs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Restaurant,
                            contentDescription = "Empty",
                            tint = TextHint,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No logged meals found for today.",
                            color = TextHint,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Search Swiggy on the chat, or log manual meals directly.",
                            color = TextHint,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // One continuous divided list instead of a card per row — data breathes,
                // it isn't boxed in five times over.
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SlateCard)
                            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                    ) {
                        todayLogs.forEachIndexed { index, mealLog ->
                            if (index > 0) HorizontalDivider(color = SlateBorder)
                            LoggedMealRow(
                                log = mealLog
                            ) { viewModel.deleteLoggedMeal(mealLog.id) }
                        }
                    }
                }
            }
        }
    }

    // Manual Log Popup dialog
    if (showManualDialog) {
        var mealName by remember { mutableStateOf("") }
        var sourceName by remember { mutableStateOf("") }
        var mealProtein by remember { mutableStateOf("20") }
        var mealCarbs by remember { mutableStateOf("30") }
        var mealFat by remember { mutableStateOf("10") }
        var mealCals by remember { mutableStateOf("290") }
        var mealCost by remember { mutableStateOf("150") }

        var foodDescription by remember { mutableStateOf("") }
        var estimateError by remember { mutableStateOf<String?>(null) }
        val isEstimating by viewModel.isEstimatingMacros.collectAsState()

        fun applyImageEstimate(result: MacroMateViewModel.FoodImageEstimate?) {
            if (result != null) {
                mealCals = result.macros.calories.toString()
                mealProtein = result.macros.protein.toString()
                mealCarbs = result.macros.carbs.toString()
                mealFat = result.macros.fat.toString()
                if (mealName.isBlank()) result.suggestedName?.let { mealName = it }
            } else {
                estimateError = "Couldn't estimate — enter macros manually below."
            }
        }

        val context = LocalContext.current
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                estimateError = null
                viewModel.estimateMacrosFromImage(bitmap, ::applyImageEstimate)
            }
        }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) cameraLauncher.launch(null) else estimateError = "Camera permission denied."
        }
        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                estimateError = null
                // ponytail: deprecated but fine at minSdk 24; switch to ImageDecoder if API 28+ becomes the floor
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                viewModel.estimateMacrosFromImage(bitmap, ::applyImageEstimate)
            }
        }

        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = {
                Text(
                    "Log External / Non-Swiggy Meal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = foodDescription,
                        onValueChange = {
                            foodDescription = it
                            estimateError = null
                        },
                        label = { Text("Describe what you ate (e.g. 2 idlis and sambar)") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("food_description_input")
                    )

                    Button(
                        onClick = {
                            estimateError = null
                            viewModel.estimateMacrosFromDescription(foodDescription) { result ->
                                if (result != null) {
                                    mealCals = result.calories.toString()
                                    mealProtein = result.protein.toString()
                                    mealCarbs = result.carbs.toString()
                                    mealFat = result.fat.toString()
                                    if (mealName.isBlank()) mealName = foodDescription
                                } else {
                                    estimateError = "Couldn't estimate — enter macros manually below."
                                }
                            }
                        },
                        enabled = !isEstimating && foodDescription.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                        modifier = Modifier.fillMaxWidth().testTag("estimate_with_ai_button")
                    ) {
                        if (isEstimating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("✨ Estimate with AI")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) },
                            enabled = !isEstimating,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                            modifier = Modifier.weight(1f).testTag("take_photo_button")
                        ) {
                            Text("📷 Take Photo")
                        }
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            enabled = !isEstimating,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                            modifier = Modifier.weight(1f).testTag("upload_photo_button")
                        ) {
                            Text("🖼️ Upload Photo")
                        }
                    }

                    estimateError?.let {
                        Text(it, color = Color(0xFFE57373), style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider(color = SlateBorder)

                    OutlinedTextField(
                        value = mealName,
                        onValueChange = { mealName = it },
                        label = { Text("Meal Name (e.g. Eggs & Whey)") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("name_input")
                    )

                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text("Restaurant / Brand (Optional)") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = mealProtein,
                            onValueChange = { mealProtein = it },
                            label = { Text("Protein (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = SlateDark, unfocusedContainerColor = SlateDark, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mealCarbs,
                            onValueChange = { mealCarbs = it },
                            label = { Text("Carbs (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = SlateDark, unfocusedContainerColor = SlateDark, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = mealFat,
                            onValueChange = { mealFat = it },
                            label = { Text("Fat (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = SlateDark, unfocusedContainerColor = SlateDark, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mealCals,
                            onValueChange = { mealCals = it },
                            label = { Text("Calories (kcal)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = SlateDark, unfocusedContainerColor = SlateDark, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = mealCost,
                        onValueChange = { mealCost = it },
                        label = { Text("Price in ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(focusedContainerColor = SlateDark, unfocusedContainerColor = SlateDark, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameStr = mealName.ifBlank { "Manual Entry" }
                        val p = mealProtein.toIntOrNull() ?: 0
                        val c = mealCarbs.toIntOrNull() ?: 0
                        val f = mealFat.toIntOrNull() ?: 0
                        val cals = mealCals.toIntOrNull() ?: 0
                        val priceVal = mealCost.toDoubleOrNull() ?: 0.0

                        viewModel.logManualMeal(nameStr, sourceName, p, c, f, cals, priceVal)
                        showManualDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    modifier = Modifier.testTag("submit_button")
                ) {
                    Text("Add Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SlateCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// Compact Horizontal macro progress items
@Composable
fun MacroBarItem(
    title: String,
    current: Int,
    limit: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val targetProgress = if (limit > 0) current.toFloat() / limit.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = minOf(1f, targetProgress),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "macroBarFill"
    )
    // Over this macro's own limit → bar + value turn red with an over flag
    val isOver = current > limit
    val overBy = current - limit
    val barColor = if (isOver) SoftRed else color
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "$current/${limit}g",
                fontSize = 10.sp,
                color = if (isOver) SoftRed else TextSecondary,
                fontWeight = if (isOver) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Progress item bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SlateBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
        if (isOver) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "▲ ${overBy}g over",
                fontSize = 9.sp,
                color = SoftRed,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Logged Meal item Row
@Composable
fun LoggedMealRow(
    log: MacroLog,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (log.source == "swiggy") OrangeSwiggy else NeonEmerald
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = log.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Text(
                text = "${log.restaurant} · ${log.calories} kcal · ₹${log.price.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Micro macro details shown in chips
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SlateDark)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    "P: ${log.proteinG}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .tactilePress(onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete Log",
                    tint = SoftRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
