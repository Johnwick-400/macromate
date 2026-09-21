package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.LightTeal
import com.example.ui.theme.OrangeSwiggy
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextHint
import com.example.ui.viewmodel.MacroMateViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MacroMateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentUserId by viewModel.currentUserId.collectAsState()
                val profile by viewModel.userProfile.collectAsState()

                if (currentUserId == null) {
                    // Show AuthScreen if not logged in
                    AuthScreen(viewModel = viewModel) {
                        // Handled automatically by currentUserId flow update
                    }
                } else if (profile == null) {
                    // Loading profile after authenticating
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SlateDark),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonEmerald)
                    }
                } else {
                    // If onboarding is incomplete, show onboarding screen, otherwise main workspace
                    val weight = profile?.weightKg ?: 0.0
                    if (weight <= 10.0) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onOnboardingComplete = {
                                // Handled automatically by userProfile emission state update
                            }
                        )
                    } else {
                        MainWorkspaceScaffold(viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainWorkspaceScaffold(viewModel: MacroMateViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val cart by viewModel.cart.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SlateDark,
        bottomBar = {
            // High fidelity bottom toolbar custom styled
            NavigationBar(
                containerColor = SlateCard,
                tonalElevation = 0.dp,
            ) {
                // Tab 1: Assistant Chat
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Concierge"
                        )
                    },
                    label = { Text("Assistant", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonEmerald,
                        selectedTextColor = NeonEmerald,
                        unselectedIconColor = TextHint,
                        unselectedTextColor = TextHint,
                        indicatorColor = LightTeal
                    ),
                    modifier = Modifier.testTag("concierge_tab")
                )

                // Tab 2: Macro Tracker Charts
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Fuel Metrics"
                        )
                    },
                    label = { Text("Tracker", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonEmerald,
                        selectedTextColor = NeonEmerald,
                        unselectedIconColor = TextHint,
                        unselectedTextColor = TextHint,
                        indicatorColor = LightTeal
                    ),
                    modifier = Modifier.testTag("tracker_tab")
                )

                // Tab 3: Swiggy Checkout Basket (with count badge!)
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (cart.isNotEmpty()) {
                                    Badge(containerColor = OrangeSwiggy) {
                                        Text(
                                            cart.sumOf { it.quantity }.toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBasket,
                                contentDescription = "Basket"
                            )
                        }
                    },
                    label = { Text("Basket", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OrangeSwiggy,
                        selectedTextColor = OrangeSwiggy,
                        unselectedIconColor = TextHint,
                        unselectedTextColor = TextHint,
                        indicatorColor = Color(0xFFFFE5D3) // Soft warm orange accent matching Swiggy styling
                    ),
                    modifier = Modifier.testTag("cart_tab")
                )

                // Tab 4: User Profile settings
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Profile", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonEmerald,
                        selectedTextColor = NeonEmerald,
                        unselectedIconColor = TextHint,
                        unselectedTextColor = TextHint,
                        indicatorColor = LightTeal
                    ),
                    modifier = Modifier.testTag("profile_tab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(300)) { it * direction } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { -it * direction } + fadeOut(tween(300)))
                },
                modifier = Modifier.fillMaxSize(),
                label = "MainDashboardTransitions"
            ) { activeTab ->
                when (activeTab) {
                    0 -> ChatScreen(viewModel = viewModel)
                    1 -> TrackerScreen(viewModel = viewModel)
                    2 -> CartScreen(viewModel = viewModel)
                    3 -> ProfileScreen(viewModel = viewModel)
                }
            }
        }
    }
}
