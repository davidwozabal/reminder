package com.wozabal.reminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wozabal.reminder.data.ActivityEntity
import com.wozabal.reminder.data.CompletionEntity
import com.wozabal.reminder.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarMonthView(
    currentDate: LocalDate,
    activities: List<ActivityEntity>,
    completionsInRange: List<CompletionEntity>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val ym = YearMonth.from(currentDate)
    val firstDay = ym.atDay(1)
    val daysInMonth = ym.lengthOfMonth()
    val firstDayOfWeek = firstDay.dayOfWeek.value // Monday=1, Sunday=7

    // Get active activities for each day
    val activeCounts = remember(activities, currentDate) {
        (1..daysInMonth).map { day ->
            val date = ym.atDay(day)
            activities.count { isActiveOnDate(it, date) && !isBeforeCreated(it, date) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Day headers
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar grid
        val totalCells = firstDayOfWeek - 1 + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - (firstDayOfWeek - 1) + 1

                    if (dayNumber in 1..daysInMonth) {
                        val date = ym.atDay(dayNumber)
                        val isToday = date == today
                        val completed = completionsInRange.count {
                            it.date == date.toString() && it.status == "DONE"
                        }
                        val total = activeCounts[dayNumber - 1]

                        DayCell(
                            day = dayNumber,
                            completed = completed,
                            total = total,
                            isToday = isToday,
                            isFuture = date.isAfter(today),
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(date) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    completed: Int,
    total: Int,
    isToday: Boolean,
    isFuture: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isToday -> TodayHighlight
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$day",
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            color = if (isToday) Primary else Color.Black
        )
        if (total > 0) {
            val ratioColor = when {
                isFuture -> FutureGray
                completed == total -> DoneGreen
                completed > 0 -> PendingOrange
                else -> MissedRed
            }
            Text(
                text = "$completed/$total",
                fontSize = 10.sp,
                color = ratioColor,
                fontWeight = FontWeight.Bold
            )
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
