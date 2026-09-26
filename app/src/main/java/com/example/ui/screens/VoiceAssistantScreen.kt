package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DishItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel

/** Floating "Talk to Buddy" button overlaid on the Assistant screen (variant B). */
@Composable
fun BuddyFab(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "buddyRipple")
    val ripple by transition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ripple"
    )
    val rippleAlpha by transition.animateFloat(
        initialValue = 0.45f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "rippleAlpha"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Talk to Buddy",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonEmerald,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SlateCard)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Spacer(Modifier.height(6.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(ripple)
                    .clip(CircleShape)
                    .background(NeonEmerald.copy(alpha = rippleAlpha))
            )
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF0A8375), Color(0xFF004D43))))
                    .tactilePress(onClick)
                    .testTag("buddy_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, "Talk to Buddy", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** Full-screen immersive voice sheet with Listening / Working / Answered / Error states. */
@Composable
fun VoiceAssistantOverlay(
    viewModel: MacroMateViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.voiceState.collectAsState()
    val transcript by viewModel.voiceTranscript.collectAsState()
    val filler by viewModel.voiceFiller.collectAsState()
    val answer by viewModel.voiceAnswer.collectAsState()
    val error by viewModel.voiceError.collectAsState()
    val activeResults by viewModel.activeResults.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0A8375), NeonEmerald, Color(0xFF004D43)),
                    radius = 1400f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(22.dp)) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Buddy", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (state) {
                            MacroMateViewModel.VoiceState.LISTENING -> "Listening…"
                            MacroMateViewModel.VoiceState.WORKING -> "Searching real menus…"
                            MacroMateViewModel.VoiceState.ANSWERED -> "Best match found"
                            MacroMateViewModel.VoiceState.ERROR -> "Something went wrong"
                            else -> "Nutritional concierge"
                        },
                        color = Color(0xFFBFE8E1), fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .tactilePress(onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Orb
            BuddyOrb(state)

            Spacer(Modifier.height(20.dp))

            // Body per state
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    MacroMateViewModel.VoiceState.LISTENING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = transcript.ifBlank { "Say something like \"strict veg meal under ₹200, 450 calories\"" },
                                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(16.dp)
                            )
                        }
                    }
                    MacroMateViewModel.VoiceState.WORKING -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(filler) { line ->
                                Text(
                                    text = line,
                                    color = Color.White, fontSize = 14.sp,
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .padding(12.dp)
                                )
                            }
                        }
                    }
                    MacroMateViewModel.VoiceState.ANSWERED -> {
                        Column {
                            Text(
                                answer, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(14.dp))
                            activeResults.firstOrNull()?.let { VoiceAnswerCard(it) { viewModel.addItemToCart(it) } }
                        }
                    }
                    MacroMateViewModel.VoiceState.ERROR -> {
                        Text(
                            error ?: "Something went wrong.",
                            color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }
            }

            // Bottom control — mic (re-ask) / retry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .tactilePress {
                            if (state == MacroMateViewModel.VoiceState.LISTENING) viewModel.stopVoiceListening()
                            else viewModel.startVoiceListening()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, "Speak", tint = NeonEmerald, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun BuddyOrb(state: MacroMateViewModel.VoiceState) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbPulse"
    )
    val active = state == MacroMateViewModel.VoiceState.LISTENING ||
            state == MacroMateViewModel.VoiceState.WORKING
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(if (active) pulse else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFEAFBF6), Color(0xFF7FE0D0), Color(0xFF16B49C)))
                )
        )
    }
}

@Composable
private fun VoiceAnswerCard(dish: DishItem, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateCard)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dish.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(dish.restaurantName, color = TextSecondary, fontSize = 11.sp)
            }
            Text("₹${dish.price.toInt()}", color = OrangeSwiggy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MacroPill("P", "${dish.proteinG}g", NeonEmerald)
            MacroPill("C", "${dish.carbsG}g", ElectricIndigo)
            MacroPill("F", "${dish.fatG}g", GoldenAmber)
            MacroPill("CAL", "${dish.calories}", TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(NeonEmerald)
                .tactilePress(onAdd)
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AddShoppingCart, "Add", tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add to cart", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
