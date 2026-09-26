package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.data.model.CartItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel

@Composable
fun CartScreen(
    viewModel: MacroMateViewModel,
) {
    val cart by viewModel.cart.collectAsState()
    val totalMacros = viewModel.getCartMacros()
    val totalPrice = viewModel.getCartTotal()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        // Checkout Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateCard)
                .padding(vertical = 16.dp, horizontal = 20.dp)
                .statusBarsPadding()
        ) {
            Column {
                Text(
                    text = "Swiggy Basket",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Seamless macro-synchronized cart",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        if (cart.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = "Empty",
                    tint = TextHint,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your Swiggy Basket is empty.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Use our MacroMate chat screen to let research agent probe Swiggy menus and recommend targeted high protein combinations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Cart Products Scroll Lists
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Checkout Items",
                            fontSize = 14.sp,
                            color = OrangeSwiggy,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(cart) { cartItem ->
                        CartProductItemRow(
                            item = cartItem,
                            onQuantityIncrease = { viewModel.updateCartQuantity(cartItem.dish.id, 1) },
                            onQuantityDecrease = { viewModel.updateCartQuantity(cartItem.dish.id, -1) }
                        ) { viewModel.removeCartItem(cartItem.dish.id) }
                    }

                    // Cart Summary Block
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SlateCard)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ORDER METRIC AGGREGATION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("PROTEIN", fontSize = 9.sp, color = TextHint)
                                        Text("${totalMacros.protein}g", fontWeight = FontWeight.Bold, color = NeonEmerald)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CARBS", fontSize = 9.sp, color = TextHint)
                                        Text("${totalMacros.carbs}g", fontWeight = FontWeight.Bold, color = ElectricIndigo)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("FAT", fontSize = 9.sp, color = TextHint)
                                        Text("${totalMacros.fat}g", fontWeight = FontWeight.Bold, color = GoldenAmber)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CALORIES", fontSize = 9.sp, color = TextHint)
                                        Text("${totalMacros.calories} kcal", fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }

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
                                    Text("Restaurant Delivery Fee", color = TextSecondary, fontSize = 13.sp)
                                    Text("Free", color = NeonEmerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("To Pay", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                                    Text("₹${totalPrice.toInt()}", fontWeight = FontWeight.Black, color = OrangeSwiggy, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }

                // CTA Tray bottom
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SlateCard,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding()
                    ) {
                        Button(
                            onClick = {
                                viewModel.confirmCartAndPlaceOrder { syncedToSwiggy, restaurantId ->
                                    Toast.makeText(
                                        context,
                                        if (syncedToSwiggy) "Items added to your Swiggy cart — opening Swiggy..."
                                        else "Opening Swiggy (connect your account in Profile to sync items directly)",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    // Swiggy has no working "open cart" link, so open the restaurant the items were
                                    // added from (or the app itself when nothing could be synced).
                                    val target = if (syncedToSwiggy && restaurantId != null) "swiggy://menu?restaurant_id=$restaurantId" else "swiggy://home"
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                                    } catch (_: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.swiggy.com/".toUri()))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeSwiggy),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = "",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Confirm & Order on Swiggy",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Basket Product Item Row
@Composable
fun CartProductItemRow(
    item: CartItem,
    onQuantityIncrease: () -> Unit,
    onQuantityDecrease: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BoxDefaults.cardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.dish.name,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 15.sp,
                )
                Text(
                    text = item.dish.restaurantName,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "P: ${item.dish.proteinG * item.quantity}g · ${item.dish.calories * item.quantity} kcal · ₹${(item.dish.price * item.quantity).toInt()}",
                    fontSize = 11.sp,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quantity controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Minus action with spring scale
                val minusInteraction = remember { MutableInteractionSource() }
                val minusPressed by minusInteraction.collectIsPressedAsState()
                val minusScale by animateFloatAsState(
                    targetValue = if (minusPressed) 0.8f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "minusScale"
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer(scaleX = minusScale, scaleY = minusScale)
                        .background(SlateDark, CircleShape)
                        .clickable(
                            interactionSource = minusInteraction,
                            indication = null
                        ) { onQuantityDecrease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Reduce",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = item.quantity.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Plus action with spring scale
                val plusInteraction = remember { MutableInteractionSource() }
                val plusPressed by plusInteraction.collectIsPressedAsState()
                val plusScale by animateFloatAsState(
                    targetValue = if (plusPressed) 0.8f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "plusScale"
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer(scaleX = plusScale, scaleY = plusScale)
                        .background(SlateDark, CircleShape)
                        .clickable(
                            interactionSource = plusInteraction,
                            indication = null
                        ) { onQuantityIncrease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Empty",
                        tint = SoftRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

object BoxDefaults {
    @Composable
    fun cardBorder() = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
}
