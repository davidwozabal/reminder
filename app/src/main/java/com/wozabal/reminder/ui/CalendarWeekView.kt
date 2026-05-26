package com.wozabal.reminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wozabal.reminder.data.ActivityEntity
import com.wozabal.reminder.data.CompletionEntity
import com.wozabal.reminder.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CalendarWeekView(
    currentDate: LocalDate,
    activities: List<ActivityEntity>,
    completionsInRange: List<CompletionEntity>,
    onActivityToggle: (Long, String, String?) -> Unit
) {
    val today = LocalDate.now()
    val weekStart = currentDate.with(DayOfWeek.MONDAY)
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val dateFmt = DateTimeFormatter.ofPattern("EEE dd")

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        items(days) { date ->
            val isToday = date == today
            val isPast = date.isBefore(today)
            val isFuture = date.isAfter(today)
            val dayActivities = activities.filter { isActiveOnDate(it, date) }
            val dayCompletions = completionsInRange.filter { it.date == date.toString() }

            val bgColor = when {
                isToday -> TodayHighlight
                isPast -> Color(0xFFFAFAFA)
                else -> Color.Transparent
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .padding(8.dp)
            ) {
                Text(
                    text = date.format(dateFmt),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    color = if (isToday) Primary else Color.Black
                )

                if (dayActivities.isEmpty()) {
                    Text(
                        text = "No activities",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                } else {
                    dayActivities.forEach { activity ->
                        val completion = dayCompletions.find { it.activityId == activity.id }
                        val status = completion?.status
                        val symbol = when {
                            status == "DONE" -> "✅"
                            status == "MISSED" -> "❌"
                            isFuture -> "⭕"
                            else -> "⏳"
                        }
                        val timeStr = String.format("%02d:%02d", activity.dueHour, activity.dueMinute)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isFuture) {
                                        onActivityToggle(activity.id, date.toString(), status)
                                    }
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(symbol, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$timeStr ${activity.name}",
                                fontSize = 13.sp,
                                color = if (status == "MISSED") MissedRed else Color.Black
                            )
                        }
                    }
                }
            }

            if (date != days.last()) {
                Spacer(modifier = Modifier.height(4.dp))
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
