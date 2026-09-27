package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.budget.BudgetPace
import com.example.domain.budget.BudgetStatus
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

private val rupeeFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 0
}

/** "₹3,800" / "₹1,20,000" — Indian digit grouping, no decimals. */
internal fun formatRupees(amount: Double): String = "₹" + rupeeFormat.format(amount.roundToLong())

/** Monthly food budget summary for the Tracker tab. [status] is null when no budget is set. */
@Composable
fun BudgetCard(
    status: BudgetStatus?,
    onSetBudget: () -> Unit,
    onOpenDietaryLog: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("budget_card"),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        if (status == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Set a monthly food budget",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "See a safe daily amount for Swiggy orders",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = onSetBudget, modifier = Modifier.testTag("set_budget_button")) {
                    Text("Set budget", color = NeonEmerald, fontWeight = FontWeight.Bold)
                }
            }
            return@Card
        }

        val fraction = status.fractionUsed.toFloat()
        val barColor = when {
            fraction > 0.90f -> SoftRed
            fraction >= 0.75f -> OrangeSwiggy
            else -> NeonEmerald
        }
        val animated by animateFloatAsState(
            targetValue = fraction.coerceIn(0f, 1f),
            animationSpec = tween(900, easing = FastOutSlowInEasing),
            label = "budgetProgress"
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MONTHLY FOOD BUDGET",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonEmerald,
                    letterSpacing = 1.2.sp
                )
                PaceBadge(status.pace)
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = SlateBorder,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Spent ${formatRupees(status.spent)} of ${formatRupees(status.budgetForPeriod)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.testTag("budget_spent")
            )
            Spacer(modifier = Modifier.height(2.dp))
            val allowance = status.dailyAllowance
            Text(
                text = if (allowance == null) "Budget used up"
                else "${formatRupees(allowance)} a day for ${status.daysLeft} ${if (status.daysLeft == 1) "day" else "days"}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (allowance == null) SoftRed else OrangeSwiggy,
                modifier = Modifier.testTag("budget_daily_allowance")
            )

            if (status.unconfirmedSpendDays.isNotEmpty()) {
                val n = status.unconfirmedSpendDays.size
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SlateBorder)
                Text(
                    text = "$n ${if (n == 1) "day" else "days"} without spend info — confirm in Dietary Log",
                    fontSize = 11.sp,
                    color = NeonEmerald,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tactilePress(onOpenDietaryLog)
                        .padding(top = 10.dp)
                        .testTag("budget_unconfirmed_days")
                )
            }
        }
    }
}

@Composable
private fun PaceBadge(pace: BudgetPace) {
    val (label, fg, bg) = when (pace) {
        BudgetPace.ON_TRACK -> Triple("On track", NeonEmerald, LightTeal)
        BudgetPace.OVER_PACE -> Triple("Over pace", GoldenAmber, Color(0xFFFFE5D3))
        BudgetPace.USED_UP -> Triple("Used up", SoftRed, Color(0xFFFDE2E2))
    }
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .testTag("budget_pace_badge")
    )
}
