# Reminder ✅

Android app for tracking daily activities with a calendar view, completion statistics, and persistent reminders with escalating notifications.

Co-developed by David Wozabal and Nancy Wozabal.

---

## Features (Planned)

### Calendar Screen
- **Monthly/Weekly toggle**: Slider to switch between views
- **Monthly view**: Grid calendar showing completion ratio per day (e.g., 3/4)
- **Weekly view**: Day-by-day activity list with visual markers:
  - ✅ = completed
  - ❌ = missed (past days)
  - ⏳ = pending (today, not yet due)
  - ⭕ = pending (future days)
- **Statistics panel** below both views: per-activity completion counts for the current month

### Persistent Notification
- Foreground service with ongoing notification showing today's pending activities
- Each activity has a clickable action in the notification to mark it as done
- Notification updates in real-time as activities are completed

### Reminders
- Each activity has a configurable due time
- **Escalating alerts**: When the due time approaches and the activity isn't completed:
  - Long vibration every 30 minutes
  - Notifications escalate until the activity is marked done or the day ends
- Uses `AlarmManager.setExactAndAllowWhileIdle` for reliable timing (same proven pattern as PowerManager)

### Activity Management
- Create, edit, delete daily activities
- Choose between one-off and recurring patterns (daily, specific weekdays, weekly)
- Set due time per activity

### Data Persistence
- Room (SQLite) — same proven stack as PowerManager
- JSON backup/restore for reinstall survival
- All data stays local on the device

---

## Technical Stack

| Layer | Technology | Version / Notes |
|---|---|---|
| **Language** | Kotlin | 1.9.22 |
| **UI Framework** | Jetpack Compose (BOM) | 2024.02.00 |
| **Compose Compiler** | Kotlin compiler extension | 1.5.10 |
| **Database** | Room (SQLite) | 2.6.1 |
| **HTTP Client** | java.net.HttpURLConnection | JDK built-in (if needed) |
| **JSON** | kotlinx.serialization | 1.6.3 |
| **DI** | Manual constructor injection | — |
| **Background** | AlarmManager + Foreground Service | Android standard |
| **Build System** | Gradle + AGP | 8.5 / 8.2.2 |
| **Minification** | R8 (ProGuard) | AGP built-in |

**Target:** Android 8.0+ (API 26) · Compiled against API 34
**Goal APK Size:** < 2 MB release (R8 minified, same range as PowerManager's ~1.4 MB)

---

## Architecture (Planned)

```
app/src/main/java/com/wozabal/reminder/
├── data/
│   ├── ActivityEntity.kt         # Room entity: name, dueTime, recurrence, enabled
│   ├── CompletionEntity.kt       # Room entity: activity_id, date, status (done/missed/pending)
│   ├── ActivityDao.kt            # Room DAO
│   ├── CompletionDao.kt          # Room DAO
│   ├── ReminderDatabase.kt       # Room database singleton
│   ├── ReminderRepository.kt     # Data layer: Room + backup/restore
│   └── BackupHelper.kt           # JSON backup/restore
├── engine/
│   └── ReminderEngine.kt         # Due-time logic, escalation scheduling, completion stats
├── ui/
│   ├── CalendarScreen.kt         # Monthly/weekly view with toggle slider
│   ├── CalendarMonthView.kt      # Monthly grid composable
│   ├── CalendarWeekView.kt       # Weekly list composable
│   ├── StatsPanel.kt             # Monthly completion statistics
│   ├── ActivityEditor.kt         # Add/edit activity dialog
│   └── theme/Theme.kt            # Material3 light color scheme
├── service/
│   ├── ReminderForegroundService.kt  # Persistent notification + actions
│   └── EscalationReceiver.kt         # AlarmManager 30-min vibration escalation
├── MainActivity.kt               # Entry point
├── ReminderApp.kt                # Application class: init, backup, alarm scheduling
└── AndroidManifest.xml
```

---

## Key Design Decisions (from PowerManager Learnings)

### What Works — Keep Doing
1. **HttpURLConnection not Ktor** — Zero dependency, most reliable. Same if any network calls are needed.
2. **Canvas-based custom UI** — Full control, no external charting/calendar library. Calendar grid drawn with Compose Canvas.
3. **kotlinx.serialization** — **CRITICAL**: Compiler plugin `kotlin("plugin.serialization")` MUST be in both build.gradle.kts files. Without it, `@Serializable` classes compile silently but crash at runtime with "Serializer not found."
4. **No Google Play Services** — Zero Firebase/FCM. Works on de-Googled devices.
5. **Manual DI** — No Hilt/Dagger. Constructor injection is sufficient for this app size.
6. **AlarmManager > WorkManager** for time-specific tasks — `setExactAndAllowWhileIdle` for exact timing. WorkManager periodic work drifts within flex windows.
7. **detectDragGestures > awaitPointerEventScope** — For repeated gesture detection on Canvas (swipe between weeks/months).
8. **Release signing with debug keystore** for dev distribution — `signingConfigs { create("release") { ... } }` using debug keystore.
9. **`setExactAndAllowWhileIdle` + self-rescheduling in `onReceive()`** — Background alarms must self-reschedule because `setRepeating()` is unreliable on Android 12+ Doze mode.
10. **BOOT_COMPLETED receiver** — Re-register all alarms after device restart.

### What We Learned — Avoid
- ❌ WorkManager for exact-time alarms (drifts)
- ❌ `awaitPointerEventScope` for repeated gestures (exits after first gesture)
- ❌ Unsigned APK (won't install)
- ❌ Network calls on `Dispatchers.Main` (wrap in `withContext(Dispatchers.IO)`)

---

## Key Design Challenges

### Notification Action Limit
Android notifications support a limited number of clickable actions (typically 3 in collapsed state, more in expanded on some devices). If a user has 5 activities, we can't put 5 clickable "Done" buttons in one notification.

**Solutions under consideration:**
- Show top 3 pending activities with "Done" buttons, rest shown as "..." with tap-to-expand
- Single "Complete" button that opens a quick-action dialog (Android 11+ notification inline reply pattern)
- Custom notification layout with a scrollable list (Android 12+ `Notification.CallStyle` or similar)
- Pragmatic fallback: notification shows count summary, tap opens app to the activity checklist

### Escalating Reminders & Battery
Long vibration every 30 minutes is a significant battery drain if running for hours. 
- Need a user-configurable escalation window (e.g., start 2h before due time)
- `VibrationEffect.createWaveform` with increasing intensity
- Should stop automatically when activity is completed or day ends

### Month Boundary Statistics
Stats need to handle month transitions cleanly. The "current month" in weekly view might span two months. Need to decide: stats for the selected week's month, or always the calendar month?

---

## Building

### Prerequisites
- JDK 17+
- Android SDK (API 34, build-tools 34.0.0)
- Gradle 8.5 (via wrapper)

### Quick Build
```bash
git clone https://github.com/davidwozabal/reminder.git
cd reminder
echo "sdk.dir=$HOME/Android" > local.properties
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## License

Private — David Wozabal and Nancy Wozabal. No public license.
