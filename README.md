# Reminder ✅

Android app for tracking daily activities with a calendar view, completion statistics, and persistent reminders with escalating notifications.

Co-developed by David Wozabal and Nancy Wozabal.

**Current version:** v1.0.0

---

## Design Spec (from David)

- **Activities per day:** 2–4 typically
- **Recurrence:** Customizable — daily or weekly on specific days
- **Multi-user:** Single user only, no data sharing between devices
- **Stats:** Monthly/weekly per-activity completion rates (updates when navigating months/weeks)
- **Notification:** Click-to-mark-done directly (buttons in notification). "Missed" (✗) button to stop reminders for that activity. No retroactive editing of past days. No multiple reminders per day per activity.
- **Escalation:** Vibrations start 1 hour before due time, repeat every 30 minutes until day end or marked done/missed.

---

## Features

### Calendar Screen
- **Monthly/Weekly toggle**: Switches between views
- **Monthly view**: Grid calendar showing completion ratio per day (e.g., 3/4). Tap a day to see detail and mark activities.
- **Weekly view**: Day-by-day activity list with visual markers:
  - ✅ = completed
  - ❌ = missed (past days)
  - ⏳ = pending (today, not yet due)
  - ⭕ = pending (future days, not tappable)
- Tap activity in week view to toggle: done → missed → unmarked
- **Statistics panel** below both views: per-activity completion rate with progress bars, computed for the visible month/week

### Persistent Notification
- Foreground service with ongoing notification showing today's pending activities
- Each activity gets a "✓" button to mark done directly from notification
- "✗ All Missed" button to dismiss all remaining activities for the day
- Notification updates instantly when activities are marked

### Escalating Reminders
- AlarmManager fires first reminder 1 hour before each activity's due time
- Long vibration (3 pulses) on each reminder
- Self-reschedules every 30 minutes until activity is marked done/missed or day ends
- Midnight cleanup resets and schedules the next day's reminders

### Activity Management
- Create, edit, delete activities with:
  - Name, due time (hour/minute picker)
  - Recurrence: Daily or Weekly (select specific days)
  - Enable/disable toggle
- No retroactive editing — past/future days are immutable after the day passes

### Data Persistence
- Room (SQLite) with JSON backup/restore for reinstall survival
- Activities and completions are backed up on every change
- Auto-restores on first launch after reinstall

---

## Technical Stack

| Layer | Technology | Version / Notes |
|---|---|---|
| **Language** | Kotlin | 1.9.22 |
| **UI Framework** | Jetpack Compose (BOM) | 2024.02.00 |
| **Compose Compiler** | Kotlin compiler extension | 1.5.10 |
| **Database** | Room (SQLite) | 2.6.1 |
| **JSON** | kotlinx.serialization | 1.6.3 |
| **DI** | Manual constructor injection | — |
| **Background** | AlarmManager + Foreground Service | Android standard |
| **Build System** | Gradle + AGP | 8.5 / 8.2.2 |
| **Minification** | R8 (ProGuard) | AGP built-in |

**Target:** Android 8.0+ (API 26) · Compiled against API 34
**APK Size:** ~1.4 MB release (R8 minified)

---

## Architecture

```
app/src/main/java/com/wozabal/reminder/
├── data/
│   ├── ActivityEntity.kt         # Room entity: name, dueTime, recurrence, enabled
│   ├── CompletionEntity.kt       # Room entity: activity_id, date, status (DONE/MISSED)
│   ├── ActivityDao.kt            # Room DAO
│   ├── CompletionDao.kt          # Room DAO
│   ├── ReminderDatabase.kt       # Room database singleton (schema v1)
│   └── ReminderRepository.kt     # Data layer: Room + backup/restore + recurrence logic
├── engine/
│   └── ReminderEngine.kt         # Due-time logic, escalation scheduling, midnight cleanup
├── ui/
│   ├── CalendarScreen.kt         # Main screen: toggle, navigation, orchestrator
│   ├── CalendarMonthView.kt      # Monthly grid composable (7-column, color-coded ratios)
│   ├── CalendarWeekView.kt       # Weekly list composable (per-day activity items)
│   ├── StatsPanel.kt             # Per-activity completion rates with progress bars
│   ├── ActivityEditor.kt         # Add/edit dialog: name, time picker, recurrence, enable
│   ├── DayDetailDialog.kt        # Day detail popup: activities list with edit/toggle
│   └── theme/Theme.kt            # Color palette
├── service/
│   ├── ReminderForegroundService.kt  # Persistent notification with ✓/✗ actions
│   └── EscalationReceiver.kt         # AlarmManager: vibrate + reschedule
├── MainActivity.kt               # Entry point, notification permission, service start
├── ReminderApp.kt                # Application class: backup restore on first launch
└── AndroidManifest.xml
```

---

## Key Design Decisions (from PowerManager Learnings)

### What Works — Keep Doing
1. **No external libraries** for HTTP, DI, or charting — Kotlin + Compose + Room is sufficient
2. **Canvas-based custom UI** — Full control, no external calendar/chart library dependency
3. **kotlinx.serialization** — **CRITICAL**: Compiler plugin `kotlin("plugin.serialization")` MUST be in both build.gradle.kts files. Without it, `@Serializable` classes compile silently but crash at runtime with "Serializer not found."
4. **No Google Play Services** — Zero Firebase/FCM. Works on de-Googled devices.
5. **Manual DI** — No Hilt/Dagger. Constructor injection is sufficient.
6. **AlarmManager > WorkManager** for time-specific tasks — `setExactAndAllowWhileIdle` for exact timing. WorkManager periodic work drifts within flex windows.
7. **Release signing with debug keystore** for dev distribution
8. **`setExactAndAllowWhileIdle` + self-rescheduling in `onReceive()`** — Background alarms must self-reschedule because `setRepeating()` is unreliable on Android 12+ Doze mode.
9. **BOOT_COMPLETED receiver** — Re-register all alarms after device restart.

### What We Learned — Avoid
- ❌ WorkManager for exact-time alarms (drifts)
- ❌ `awaitPointerEventScope` for repeated gestures (exits after first gesture)
- ❌ Unsigned APK (won't install)
- ❌ Network calls on `Dispatchers.Main` (wrap in `withContext(Dispatchers.IO)`)

---

## Design Decisions (Reminder-specific)

### Recurrence Model
- **Daily** activities appear every day
- **Weekly** activities use a bitmask over 7 days (bit 0 = Monday, ..., bit 6 = Sunday)
- No monthly or custom-interval recurrence (fits the 2–4 daily activities spec)

### Notification Design
- Android supports 3 visible notification actions + more in expanded view
- With 2–4 activities, we show up to 4 "✓ ActivityName" buttons + 1 "✗ All Missed"
- Future days are not editable — tapping a future activity does nothing
- Past days: toggle between Done → Missed → unmarked

### Escalation Logic
- Reminders start 1 hour before due time
- If the 1-hour mark has already passed (app opened late), the next alarm fires at the next 30-minute boundary
- End of day is 23:59:59 — no reminders after that
- Midnight cleanup alarm schedules the next day

---

## Version History

| Version | Date | Changes |
|---|---|---|
| v1.0.0 | 2026-05-26 | Initial build: Room DB, calendar (month/week views), activity CRUD, persistent notification with actions, escalating AlarmManager reminders, backup/restore |

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
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

### Install
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

### Troubleshooting
- **APK won't install**: Make sure the APK is signed. The release build uses the debug keystore automatically.
- **No notifications**: On Android 12+, grant notification permission on first launch. For exact alarms, go to Settings → Apps → Reminder → Alarms & reminders and enable.
- **Notifications not resuming after reboot**: The `BOOT_COMPLETED` receiver re-schedules alarms after device restart.

---

## License

Private — David Wozabal and Nancy Wozabal. No public license.
