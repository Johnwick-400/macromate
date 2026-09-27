package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.schedule.MealSlotType
import com.example.ui.theme.LightTeal
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextPrimary

/** Sections a meal can be logged to, in day order. */
internal val PICKABLE_SLOTS = listOf(
    MealSlotType.BREAKFAST, MealSlotType.LUNCH, MealSlotType.SNACK, MealSlotType.DINNER,
    MealSlotType.PRE_WORKOUT, MealSlotType.POST_WORKOUT, MealSlotType.PRE_SLEEP
)

internal fun slotTypeOf(name: String?): MealSlotType? = name?.let { runCatching { MealSlotType.valueOf(it) }.getOrNull() }

/** Compact chip showing the Dietary Log section ("Lunch ▾"); tap to choose another. */
@Composable
internal fun MealSlotPicker(
    selected: MealSlotType?,
    onSelect: (MealSlotType) -> Unit,
    modifier: Modifier = Modifier,
    prefix: String = ""
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AssistChip(
            onClick = { open = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$prefix${selected?.label ?: "Choose meal"}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Change meal", modifier = Modifier.size(18.dp))
                }
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = LightTeal, labelColor = NeonEmerald)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PICKABLE_SLOTS.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label, color = if (type == selected) NeonEmerald else TextPrimary) },
                    onClick = {
                        open = false
                        onSelect(type)
                    }
                )
            }
        }
    }
}
