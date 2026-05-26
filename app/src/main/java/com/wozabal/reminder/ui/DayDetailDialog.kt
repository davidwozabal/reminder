package com.wozabal.reminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wozabal.reminder.data.ActivityEntity
import com.wozabal.reminder.data.CompletionEntity
import com.wozabal.reminder.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DayDetailDialog(
    date: LocalDate,
    activities: List<ActivityEntity>,
    completionsInRange: List<CompletionEntity>,
    onToggle: (Long, String, String?) -> Unit,
    onDismiss: () -> Unit,
    onEditActivity: (ActivityEntity) -> Unit
) {
    val today = LocalDate.now()
    val isFuture = date.isAfter(today)
    val isToday = date == today
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    val dayActivities = activities.filter { isActiveOnDate(it, date) && !isBeforeCreated(it, date) }
    val dayCompletions = completionsInRange.filter { it.date == date.toString() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = date.format(dateFmt),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isToday) Primary else Color.Black
                )

                if (isFuture) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You can only mark activities on today or past days.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else if (dayActivities.isEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No activities scheduled for this day.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    dayActivities.forEach { activity ->
                        val completion = dayCompletions.find { it.activityId == activity.id }
                        val status = completion?.status
                        val symbol = when (status) {
                            "DONE" -> "✅"
                            "MISSED" -> "❌"
                            else -> "⏳"
                        }
                        val timeStr = String.format("%02d:%02d", activity.dueHour, activity.dueMinute)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(activity.id, date.toString(), status) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(symbol, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activity.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("Due: $timeStr", fontSize = 12.sp, color = Color.Gray)
                            }
                            TextButton(onClick = { onEditActivity(activity) }) {
                                Text("Edit", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

private fun isActiveOnDate(activity: ActivityEntity, date: LocalDate): Boolean {
    if (!activity.enabled) return false
    return when (activity.recurrenceType) {
        "DAILY" -> true
        "WEEKLY" -> {
            val dayBit = 1 shl (date.dayOfWeek.value - 1)
            (activity.recurrenceDays and dayBit) != 0
        }
        else -> false
    }
}

private fun isBeforeCreated(activity: ActivityEntity, date: LocalDate): Boolean {
    if (activity.createdAt.isEmpty()) return false
    return try {
        date.isBefore(java.time.LocalDate.parse(activity.createdAt))
    } catch (_: Exception) {
        false
    }
}
