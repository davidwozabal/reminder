package com.wozabal.reminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wozabal.reminder.data.ActivityEntity
import com.wozabal.reminder.data.CompletionEntity
import com.wozabal.reminder.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsPanel(
    activities: List<ActivityEntity>,
    completionsInRange: List<CompletionEntity>,
    currentDate: LocalDate,
    viewMode: String
) {
    if (activities.isEmpty()) return

    // Determine the range for stats (month or week)
    val range = if (viewMode == "MONTH") {
        val ym = YearMonth.from(currentDate)
        ym.atDay(1) to ym.atEndOfMonth()
    } else {
        val weekStart = currentDate.with(DayOfWeek.MONDAY)
        weekStart to weekStart.plusDays(6)
    }

    val today = LocalDate.now()
    val endDate = if (range.second.isAfter(today)) today else range.second

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Statistics",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.DarkGray
        )
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))

        activities.forEach { activity ->
            val activityCompletions = completionsInRange.filter {
                it.activityId == activity.id &&
                it.date >= range.first.toString() &&
                it.date <= endDate.toString()
            }
            val done = activityCompletions.count { it.status == "DONE" }
            val missed = activityCompletions.count { it.status == "MISSED" }
            val totalDays = activityCompletions.size

            val totalPossible = (range.first.datesUntil(endDate.plusDays(1)))
                .filter { isActiveOnDate(activity, it) }
                .count()
                .toInt()

            if (totalPossible > 0) {
                val rate = (done * 100) / totalPossible
                val barColor = when {
                    rate >= 80 -> DoneGreen
                    rate >= 50 -> PendingOrange
                    else -> MissedRed
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = activity.name,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(0.35f)
                    )
                    // Progress bar
                    LinearProgressIndicator(
                        progress = { rate / 100f },
                        modifier = Modifier
                            .weight(0.45f)
                            .height(10.dp)
                            .padding(horizontal = 8.dp),
                        color = barColor,
                        trackColor = Color(0xFFE0E0E0)
                    )
                    Text(
                        text = "$done/$totalPossible",
                        fontSize = 11.sp,
                        color = barColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.2f)
                    )
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

private fun LocalDate.datesUntil(end: LocalDate): List<LocalDate> {
    val result = mutableListOf<LocalDate>()
    var current = this
    while (!current.isAfter(end)) {
        result.add(current)
        current = current.plusDays(1)
    }
    return result
}
