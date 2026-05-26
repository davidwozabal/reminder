package com.wozabal.reminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.wozabal.reminder.data.ReminderDatabase
import com.wozabal.reminder.data.ReminderRepository
import com.wozabal.reminder.ui.theme.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    db: ReminderDatabase,
    onRequestNotificationPermission: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { ReminderRepository(db, context) }
    val scope = rememberCoroutineScope()

    val activities by repo.getAllActivities().collectAsState(initial = emptyList())
    var viewMode by remember { mutableStateOf("MONTH") } // "MONTH" or "WEEK"
    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    // Completions for visible range
    var completionsInRange by remember { mutableStateOf<List<CompletionEntity>>(emptyList()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    // Reload completions when range or activities change
    LaunchedEffect(currentDate, viewMode, activities) {
        val range = if (viewMode == "MONTH") {
            val ym = YearMonth.from(currentDate)
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()
            start to end
        } else {
            val weekStart = currentDate.with(DayOfWeek.MONDAY)
            val weekEnd = weekStart.plusDays(6)
            weekStart to weekEnd
        }
        completionsInRange = repo.getCompletionsForRange(range.first.toString(), range.second.toString())
    }

    // Show activity editor dialog
    var showEditor by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<ActivityEntity?>(null) }

    // Day detail dialog
    var showDayDetail by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminder", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = OnPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingActivity = null; showEditor = true },
                containerColor = Primary
            ) {
                Text("+", fontSize = 24.sp, color = OnPrimary)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // View mode toggle
            ViewModeToggle(viewMode) { viewMode = it }

            // Navigation header
            NavigationHeader(currentDate, viewMode) { currentDate = it }

            // Calendar view
            if (viewMode == "MONTH") {
                CalendarMonthView(
                    currentDate = currentDate,
                    activities = activities,
                    completionsInRange = completionsInRange,
                    onDayClick = { day -> showDayDetail = day }
                )
            } else {
                CalendarWeekView(
                    currentDate = currentDate,
                    activities = activities,
                    completionsInRange = completionsInRange,
                    onActivityToggle = { activityId, date, currentStatus ->
                        scope.launch {
                            if (currentStatus == null) {
                                // Mark as done (or tap again for missed)
                                repo.markActivity(activityId, date, "DONE")
                            } else if (currentStatus == "DONE") {
                                repo.markActivity(activityId, date, "MISSED")
                            } else {
                                repo.unmarkActivity(activityId, date)
                            }
                            // Refresh
                            val range = currentDate.with(DayOfWeek.MONDAY).let { ws ->
                                ws to ws.plusDays(6)
                            }
                            completionsInRange = repo.getCompletionsForRange(
                                range.first.toString(), range.second.toString()
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats panel
            StatsPanel(
                activities = activities,
                completionsInRange = completionsInRange,
                currentDate = currentDate,
                viewMode = viewMode
            )
        }
    }

    // Activity editor dialog
    if (showEditor) {
        ActivityEditorDialog(
            activity = editingActivity,
            onDismiss = { showEditor = false; editingActivity = null },
            onSave = { activity ->
                scope.launch {
                    if (activity.id == 0L) {
                        repo.insertActivity(activity)
                    } else {
                        repo.updateActivity(activity)
                    }
                }
                showEditor = false
                editingActivity = null
            },
            onDelete = if (editingActivity != null) {
                { activity ->
                    scope.launch { repo.deleteActivity(activity) }
                    showEditor = false
                    editingActivity = null
                }
            } else null
        )
    }

    // Day detail dialog
    showDayDetail?.let { day ->
        DayDetailDialog(
            date = day,
            activities = activities,
            completionsInRange = completionsInRange,
            onToggle = { activityId, date, currentStatus ->
                scope.launch {
                    if (currentStatus == null) {
                        repo.markActivity(activityId, date, "DONE")
                    } else if (currentStatus == "DONE") {
                        repo.markActivity(activityId, date, "MISSED")
                    } else {
                        repo.unmarkActivity(activityId, date)
                    }
                    val range = if (viewMode == "MONTH") {
                        val ym = YearMonth.from(currentDate)
                        ym.atDay(1) to ym.atEndOfMonth()
                    } else {
                        currentDate.with(DayOfWeek.MONDAY).let { it to it.plusDays(6) }
                    }
                    completionsInRange = repo.getCompletionsForRange(
                        range.first.toString(), range.second.toString()
                    )
                }
            },
            onDismiss = { showDayDetail = null },
            onEditActivity = { activity ->
                editingActivity = activity
                showEditor = true
                showDayDetail = null
            }
        )
    }
}

@Composable
fun ViewModeToggle(current: String, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        val tabs = listOf("MONTH" to "Month", "WEEK" to "Week")
        tabs.forEach { (mode, label) ->
            val isSelected = current == mode
            Text(
                text = label,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Primary else Color.Transparent)
                    .border(1.dp, Primary, RoundedCornerShape(8.dp))
                    .clickable { onToggle(mode) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                color = if (isSelected) OnPrimary else Primary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
            if (mode == "MONTH") Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun NavigationHeader(currentDate: LocalDate, viewMode: String, onNavigate: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = {
            onNavigate(if (viewMode == "MONTH") currentDate.minusMonths(1) else currentDate.minusWeeks(1))
        }) { Text("<", fontSize = 18.sp) }

        Text(
            text = if (viewMode == "MONTH") {
                currentDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            } else {
                val weekStart = currentDate.with(DayOfWeek.MONDAY)
                val weekEnd = weekStart.plusDays(6)
                val fmt = DateTimeFormatter.ofPattern("MMM d")
                "${weekStart.format(fmt)} – ${weekEnd.format(fmt)}"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        TextButton(onClick = {
            onNavigate(if (viewMode == "MONTH") currentDate.plusMonths(1) else currentDate.plusWeeks(1))
        }) { Text(">", fontSize = 18.sp) }
    }
}
