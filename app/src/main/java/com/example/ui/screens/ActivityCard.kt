package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.repository.StepMath
import com.example.data.repository.StepSource
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel

/** Calories consumed vs. calories burned from walking, as two doughnuts side by side. */
@Composable
fun ActivityCard(
    viewModel: MacroMateViewModel,
    consumedKcal: Int,
    limitKcal: Int,
    weightKg: Double,
    heightCm: Double
) {
    val steps by viewModel.todaySteps.collectAsState()
    val source by viewModel.stepSource.collectAsState()
    val context = LocalContext.current

    fun activityPermissionGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

    var permitted by remember { mutableStateOf(activityPermissionGranted()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        viewModel.startStepTracking() // re-pick the best sensor now that permission changed
    }

    val burned = StepMath.caloriesBurned(steps, weightKg, heightCm)
    val burnGoal = StepMath.caloriesBurned(StepMath.DAILY_STEP_GOAL, weightKg, heightCm).coerceAtLeast(1)
    val consumedOver = consumedKcal > limitKcal
    val net = consumedKcal - burned

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_card"),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ACTIVITY & ENERGY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NeonEmerald,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DonutGauge(
                    progress = consumedKcal.toFloat() / limitKcal.coerceAtLeast(1),
                    color = if (consumedOver) SoftRed else NeonEmerald,
                    value = consumedKcal.toString(),
                    caption = "of $limitKcal kcal",
                    label = "Consumed"
                )
                DonutGauge(
                    progress = burned.toFloat() / burnGoal,
                    color = OrangeSwiggy,
                    value = burned.toString(),
                    caption = "of $burnGoal kcal",
                    label = "Burned"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Steps today", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = "%,d / %,d".format(steps, StepMath.DAILY_STEP_GOAL),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.testTag("steps_today")
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Net intake", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = "$net kcal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            if (source == StepSource.NONE) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No motion sensor found on this device, so steps can't be tracked.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            } else if (!permitted) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateDark)
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Basic motion sensing is on. Allow activity access for accurate all-day counting.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                        modifier = Modifier.testTag("enable_step_permission")
                    ) {
                        Text("Allow", color = NeonEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutGauge(
    progress: Float,
    color: Color,
    value: String,
    caption: String,
    label: String
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "donut$label"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
            Canvas(modifier = Modifier.size(112.dp)) {
                val strokeWidth = 13.dp.toPx()
                drawCircle(color = SlateBorder, style = Stroke(width = strokeWidth))
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = animated * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(caption, fontSize = 10.sp, color = TextSecondary)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
