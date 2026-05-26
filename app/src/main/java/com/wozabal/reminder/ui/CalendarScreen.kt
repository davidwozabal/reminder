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
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    db: ReminderDatabase,
    onRequestNotificationPermission: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { 
        try {
            ReminderRepository(db, context)
        } catch (e: Exception) {
            android.util.Log.e("ReminderRepository", "Init failed", e)
            throw e
        }
    }
    val scope = rememberCoroutineScope()

    // Add error-catching scope for all UI coroutines
    val safeScope = remember {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
                android.util.Log.e("CalendarScreen", "Coroutine exception", e)
            }
        )
    }

    val activities by repo.getAllActivities().collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(0) } // 0 = Calendar, 1 = Manage Tasks
    var viewMode by remember { mutableStateOf("MONTH") }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    var completionsInRange by remember { mutableStateOf<List<CompletionEntity>>(emptyList()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Reload completions
    LaunchedEffect(currentDate, viewMode, activities) {
        try {
            val range = if (viewMode == "MONTH") {
                val ym = YearMonth.from(currentDate)
                ym.atDay(1) to ym.atEndOfMonth()
            } else {
                currentDate.with(DayOfWeek.MONDAY).let { it to it.plusDays(6) }
            }
            completionsInRange = repo.getCompletionsForRange(range.first.toString(), range.second.toString())
            loadError = null
        } catch (e: Exception) {
            android.util.Log.e("CalendarScreen", "Load completions failed", e)
            loadError = e.message ?: "Unknown error"
        }
    }

    // Dialogs
    var showEditor by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<ActivityEntity?>(null) }
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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("Calendar") },
                    icon = { Text("📅", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("Tasks") },
                    icon = { Text("⚙️", fontSize = 18.sp) }
                )
            }
        }
    ) { padding ->
        if (tab == 0) {
            CalendarTab(
                modifier = Modifier.padding(padding),
                viewMode = viewMode,
                onToggleViewMode = { viewMode = it },
                currentDate = currentDate,
                onNavigate = { currentDate = it },
                activities = activities,
                completionsInRange = completionsInRange,
                onDayClick = { showDayDetail = it },
                onActivityToggle = { activityId, date, currentStatus ->
                    safeScope.launch {
                        if (currentStatus == null) {
                            repo.markActivity(activityId, date, "DONE")
                        } else if (currentStatus == "DONE") {
                            repo.markActivity(activityId, date, "MISSED")
                        } else {
                            repo.unmarkActivity(activityId, date)
                        }
                        val range = if (viewMode == "MONTH") {
                            YearMonth.from(currentDate).atDay(1) to YearMonth.from(currentDate).atEndOfMonth()
                        } else {
                            currentDate.with(DayOfWeek.MONDAY).let { it to it.plusDays(6) }
                        }
                        completionsInRange = repo.getCompletionsForRange(range.first.toString(), range.second.toString())
                    }
                },
                onAddActivity = {
                    editingActivity = null
                    showEditor = true
                }
            )
        } else {
            ManageTasksTab(
                modifier = Modifier.padding(padding),
                activities = activities,
                onEdit = { activity ->
                    editingActivity = activity
                    showEditor = true
                },
                onAdd = {
                    editingActivity = null
                    showEditor = true
                }
            )
        }
    }

    // Activity editor dialog
    if (showEditor) {
        ActivityEditorDialog(
            activity = editingActivity,
            onDismiss = { showEditor = false; editingActivity = null },
            onSave = { activity ->
                safeScope.launch {
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
                    safeScope.launch { repo.deleteActivity(activity) }
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
                safeScope.launch {
                    if (currentStatus == null) {
                        repo.markActivity(activityId, date, "DONE")
                    } else if (currentStatus == "DONE") {
                        repo.markActivity(activityId, date, "MISSED")
                    } else {
                        repo.unmarkActivity(activityId, date)
                    }
                    val range = if (viewMode == "MONTH") {
                        YearMonth.from(currentDate).atDay(1) to YearMonth.from(currentDate).atEndOfMonth()
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

// ── Calendar Tab ──────────────────────────────────────────────

@Composable
fun CalendarTab(
    modifier: Modifier = Modifier,
    viewMode: String,
    onToggleViewMode: (String) -> Unit,
    currentDate: LocalDate,
    onNavigate: (LocalDate) -> Unit,
    activities: List<ActivityEntity>,
    completionsInRange: List<CompletionEntity>,
    onDayClick: (LocalDate) -> Unit,
    onActivityToggle: (Long, String, String?) -> Unit,
    onAddActivity: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        ViewModeToggle(viewMode, onToggleViewMode)

        NavigationHeader(currentDate, viewMode, onNavigate)

        if (viewMode == "MONTH") {
            CalendarMonthView(
                currentDate = currentDate,
                activities = activities,
                completionsInRange = completionsInRange,
                onDayClick = onDayClick
            )
        } else {
            CalendarWeekView(
                currentDate = currentDate,
                activities = activities,
                completionsInRange = completionsInRange,
                onActivityToggle = onActivityToggle
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        StatsPanel(
            activities = activities,
            completionsInRange = completionsInRange,
            currentDate = currentDate,
            viewMode = viewMode
        )

        Spacer(modifier = Modifier.weight(1f))

        // Add button
        Button(
            onClick = onAddActivity,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text("+ Add Activity", color = OnPrimary)
        }
    }
}

// ── Manage Tasks Tab ──────────────────────────────────────────

@Composable
fun ManageTasksTab(
    modifier: Modifier = Modifier,
    activities: List<ActivityEntity>,
    onEdit: (ActivityEntity) -> Unit,
    onAdd: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Manage Tasks",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, 12.dp)
        )

        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No tasks yet. Add one below!", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activities) { activity ->
                    ActivityCard(
                        activity = activity,
                        onClick = { onEdit(activity) }
                    )
                }
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text("+ Add Activity", color = OnPrimary)
        }
    }
}

@Composable
fun ActivityCard(
    activity: ActivityEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (activity.enabled) Color.White else Color(0xFFF0F0F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (activity.enabled) DoneGreen else Color.Gray)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildActivityInfo(activity),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Text("›", fontSize = 20.sp, color = Color.Gray)
        }
    }
}

private fun buildActivityInfo(activity: ActivityEntity): String {
    val time = "${activity.dueHour.toString().padStart(2, '0')}:${activity.dueMinute.toString().padStart(2, '0')}"
    val recurrence = when (activity.recurrenceType) {
        "DAILY" -> "Daily"
        "WEEKLY" -> {
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val days = (0..6).filter { (activity.recurrenceDays and (1 shl it)) != 0 }
                .map { dayNames[it] }
            days.joinToString(", ")
        }
        else -> "?"
    }
    val since = if (activity.createdAt.isNotEmpty()) " (since ${activity.createdAt.substring(5)})" else ""
    return "$time • $recurrence$since"
}

// ── View Utilities ────────────────────────────────────────────

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
