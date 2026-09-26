package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.repository.AgentLlm
import com.example.data.model.DishItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: MacroMateViewModel,
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isAgentThinking by viewModel.isAgentThinking.collectAsState()
    val thinkingSteps by viewModel.thinkingSteps.collectAsState()
    val activeResults by viewModel.activeResults.collectAsState()
    val cart by viewModel.cart.collectAsState()

    var inputQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Speech-to-Text launcher for instant voice queries
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputQuery = spoken
                focusManager.clearFocus()
                viewModel.sendMessage(spoken)
            }
        }
    }

    // Dialog state for "Why this?"
    var infoDialogDish by remember { mutableStateOf<DishItem?>(null) }
    var showInfoDialog by remember { mutableStateOf(value = false) }

    // Quick-prompts
    val quickPrompts = listOf(
        "Post-workout dinner",
        "High protein lunch under ₹300",
        "Keto breakfast",
        "Bulk meal 800+ cal"
    )

    // Auto-scroll to end when messages grow
    LaunchedEffect(messages.size, isAgentThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        // Chat screen header indicating AI Provider status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateCard)
                .padding(vertical = 16.dp, horizontal = 20.dp)
                .statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Breathing status dot — a calm perpetual pulse instead of a static marker
                    val pulseTransition = rememberInfiniteTransition(label = "statusPulse")
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "statusPulseAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(NeonEmerald)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MacroMate Concierge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        val swiggyLinked by viewModel.swiggyLinked.collectAsState()
                        Text(
                            text = if (swiggyLinked) "Status: Live Swiggy data" else "Status: Offline demo data",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Badge showing which model actually powers the agent right now
                val qwen by viewModel.qwenState.collectAsState()
                val brainLabel = remember(qwen) {
                    when (AgentLlm.mode()) {
                        AgentLlm.Mode.LOCAL -> "QWEN 2.5 · ON-DEVICE"
                        AgentLlm.Mode.CLOUD -> "GEMINI · CLOUD"
                        AgentLlm.Mode.NONE -> "OFFLINE"
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateDark)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = brainLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Quick prompt chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateBorder, RoundedCornerShape(20.dp))
                        .tactilePress {
                            focusManager.clearFocus()
                            viewModel.sendMessage(prompt)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(prompt, color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Chat Message Log
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message, modifier = Modifier.animateItem())
            }

            // Inline Loading / Agent Thinking logs representation
            if (isAgentThinking) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateCard)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
                            val delays = listOf(0, 167, 334)
                            val dotAlphas = delays.map { delayMs ->
                                infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = keyframes {
                                            durationMillis = 900
                                            1f at 300
                                            0.3f at 600
                                        },
                                        initialStartOffset = StartOffset(delayMs)
                                    ),
                                    label = "dot$delayMs"
                                )
                            }
                            dotAlphas.forEach { alphaState ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .alpha(alphaState.value)
                                        .clip(CircleShape)
                                        .background(NeonEmerald)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MacroMate Agent Thinking...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic thinking logs — each new step waterfalls in instead of popping in flatly
                        thinkingSteps.forEachIndexed { index, step ->
                            var stepVisible by remember(step, index) { mutableStateOf(false) }
                            LaunchedEffect(step, index) { stepVisible = true }
                            AnimatedVisibility(
                                visible = stepVisible,
                                enter = fadeIn(UiSpring) + slideInVertically(initialOffsetY = { it / 3 })
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = NeonEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Meal Results Overlay Screen ---
        // Shown directly over chat logs when active results are present
        AnimatedVisibility(
            visible = activeResults.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateCard)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(vertical = 16.dp)
            ) {
                // Results Header with Close action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "",
                                tint = OrangeSwiggy,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Matched Macro Discoveries",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Swipe cards left or right to dismiss",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SlateDark)
                            .tactilePress { viewModel.clearResults() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Results",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable results cards with Swipe to Dismiss simulation built-in!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeResults, key = { it.id }) { dish ->
                        val quantityInCart = cart.find { it.dish.id == dish.id }?.quantity ?: 0
                        SwipeableMealCard(
                            dish = dish,
                            quantityInCart = quantityInCart,
                            onDismiss = {
                                viewModel.dismissResult(dish.id)
                            },
                            onAddToCart = {
                                viewModel.addItemToCart(dish)
                            },
                            onIncrease = {
                                viewModel.addItemToCart(dish)
                            },
                            onDecrease = {
                                viewModel.updateCartQuantity(dish.id, -1)
                            },
                            onWhyRequired = {
                                infoDialogDish = dish
                                showInfoDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Input bottom tray
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SlateCard,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text field
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    placeholder = {
                        Text(
                            "Find post-workout chicken rolls...",
                            color = TextHint,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("submit_button"),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SlateDark,
                        unfocusedContainerColor = SlateDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedIndicatorColor = NeonEmerald,
                        unfocusedIndicatorColor = SlateBorder
                    ),
                    shape = RoundedCornerShape(24.dp)
                )

                // Action button: Send if text entered, or Mic for on-device voice input
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputQuery.isNotBlank()) OrangeSwiggy else NeonEmerald)
                        .tactilePress {
                            if (inputQuery.isNotBlank()) {
                                val q = inputQuery
                                inputQuery = ""
                                focusManager.clearFocus()
                                viewModel.sendMessage(q)
                            } else {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "What would you like to eat?")
                                }
                                speechLauncher.launch(intent)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (inputQuery.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                        contentDescription = if (inputQuery.isNotBlank()) "Send" else "Voice Input",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // "Why this?" Diagnostic popup dialog
    if (showInfoDialog && (infoDialogDish != null)) {
        val d = infoDialogDish!!
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "",
                        tint = NeonEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Why MacroMate picked this",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column {
                    Text(d.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    Text(d.restaurantName, color = OrangeSwiggy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = d.aiReasoning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateDark)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PROTEIN", fontSize = 10.sp, color = TextSecondary)
                            Text("${d.proteinG}g", fontWeight = FontWeight.Bold, color = NeonEmerald)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CARBS", fontSize = 10.sp, color = TextSecondary)
                            Text("${d.carbsG}g", fontWeight = FontWeight.Bold, color = ElectricIndigo)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FAT", fontSize = 10.sp, color = TextSecondary)
                            Text("${d.fatG}g", fontWeight = FontWeight.Bold, color = GoldenAmber)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
                ) {
                    Text("Got it")
                }
            },
            containerColor = SlateCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// --- Chat Bubble custom styling ---
@Composable
fun ChatBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.sender == "USER"
    val isSystem = message.sender == "SYSTEM"

    if (isSystem) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = NeonEmerald,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateCard)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentWidth(align = if (isUser) Alignment.End else Alignment.Start)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 16.dp
                        )
                    )
                    .background(if (isUser) OrangeSwiggy else SlateCard)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = if (isUser) "You" else "MacroMate AI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) Color.White.copy(alpha = 0.8f) else NeonEmerald,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = message.content,
                        color = if (isUser) Color.White else TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// --- Swipeable Meal Card with animation ---
@Composable
fun SwipeableMealCard(
    dish: DishItem,
    quantityInCart: Int,
    onDismiss: () -> Unit,
    onAddToCart: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onWhyRequired: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(dismissed) {
        if (dismissed) {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    offsetX += delta
                },
                onDragStopped = {
                    if (offsetX > 400f || offsetX < -400f) {
                        dismissed = true
                    } else {
                        offsetX = 0f
                    }
                }
            )
            .clip(RoundedCornerShape(12.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dish.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dish.restaurantName + " · " + dish.deliveryTimeMin + " mins",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "₹${dish.price.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OrangeSwiggy
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Macro Pills Row with stagger entrance
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val pillData = listOf(
                    Triple("P", "${dish.proteinG}g", NeonEmerald),
                    Triple("C", "${dish.carbsG}g", ElectricIndigo),
                    Triple("F", "${dish.fatG}g", GoldenAmber),
                    Triple("CAL", "${dish.calories} kcal", TextSecondary)
                )
                pillData.forEachIndexed { index, (tag, value, color) ->
                    var pillVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 80L)
                        pillVisible = true
                    }
                    AnimatedVisibility(
                        visible = pillVisible,
                        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f)
                    ) {
                        MacroPill(tag = tag, value = value, color = color)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action CTAs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Why This Button
                TextButton(
                    onClick = onWhyRequired,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "Reason",
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Why this?", color = TextSecondary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Add To Cart — flips to "Added ✓" + quantity stepper once in cart
                if (quantityInCart <= 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonEmerald)
                            .tactilePress(onAddToCart)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddShoppingCart,
                            contentDescription = "Add",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add to cart", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Added",
                            tint = NeonEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Added", color = NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.width(10.dp))

                        // Quantity stepper
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, NeonEmerald, RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .tactilePress(onDecrease),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease",
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = quantityInCart.toString(),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 22.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .tactilePress(onIncrease),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacroPill(tag: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$tag: $value",
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
