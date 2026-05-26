package com.wozabal.reminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wozabal.reminder.data.ActivityEntity
import com.wozabal.reminder.ui.theme.*

@Composable
fun ActivityEditorDialog(
    activity: ActivityEntity?,
    onDismiss: () -> Unit,
    onSave: (ActivityEntity) -> Unit,
    onDelete: ((ActivityEntity) -> Unit)?
) {
    var name by remember { mutableStateOf(activity?.name ?: "") }
    var dueHour by remember { mutableIntStateOf(activity?.dueHour ?: 8) }
    var dueMinute by remember { mutableIntStateOf(activity?.dueMinute ?: 0) }
    var recurrenceType by remember { mutableStateOf(activity?.recurrenceType ?: "DAILY") }
    var recurrenceDays by remember { mutableIntStateOf(activity?.recurrenceDays ?: 0x7F) } // all days by default
    var enabled by remember { mutableStateOf(activity?.enabled ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (activity == null) "New Activity" else "Edit Activity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Due time
                Text("Due Time", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { if (dueHour < 23) dueHour++ }) { Text("▲") }
                        Text(
                            text = String.format("%02d", dueHour),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { if (dueHour > 0) dueHour-- }) { Text("▼") }
                    }
                    Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    // Minute picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { if (dueMinute < 59) dueMinute++ }) { Text("▲") }
                        Text(
                            text = String.format("%02d", dueMinute),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { if (dueMinute > 0) dueMinute-- }) { Text("▼") }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recurrence type
                Text("Repeats", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = recurrenceType == "DAILY",
                        onClick = { recurrenceType = "DAILY" },
                        label = { Text("Daily") }
                    )
                    FilterChip(
                        selected = recurrenceType == "WEEKLY",
                        onClick = { recurrenceType = "WEEKLY" },
                        label = { Text("Weekly") }
                    )
                }

                // Day selector (weekly only)
                if (recurrenceType == "WEEKLY") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("On days", fontSize = 13.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val dayNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
                        dayNames.forEachIndexed { index, label ->
                            val bit = 1 shl index
                            val isSelected = (recurrenceDays and bit) != 0
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    recurrenceDays = if (isSelected) recurrenceDays and bit.inv()
                                    else recurrenceDays or bit
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Enabled toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enabled", fontSize = 14.sp)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (onDelete != null && activity != null) {
                        TextButton(
                            onClick = { onDelete(activity) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MissedRed)
                        ) {
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSave(
                                        ActivityEntity(
                                            id = activity?.id ?: 0L,
                                            name = name.trim(),
                                            dueHour = dueHour,
                                            dueMinute = dueMinute,
                                            recurrenceType = recurrenceType,
                                            recurrenceDays = if (recurrenceType == "DAILY") 0x7F else recurrenceDays,
                                            enabled = enabled
                                        )
                                    )
                                }
                            },
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
