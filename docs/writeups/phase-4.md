# Phase 4 Writeup: Feedback Loop

Nov 7, 2026. Phase 4 is on the `phase-4` branch.

## Status

**Phase 4 MVP is built and compiles.** The app now has morning check-ins that correct the DLMO estimate, goal completion with a maintenance mode, notification reminders for every plan item, a background worker for daily sync, a progress screen, and a settings screen. Tests and on-device verification are deferred to a follow-up pass.

## What was implemented and how

### Morning check-in and DLMO correction (FR-8)

- `ProcessCheckInUseCase` records how alert the user feels (1–5) and compares it to the model's forecast at that hour. A mismatch of 0.2 or more on two consecutive mornings in the same direction triggers a correction: 15 min for a 1-point gap, 30 min for 2+. If the user feels more tired than predicted, DLMO shifts later; more alert shifts it earlier.
- `CheckInScreen` shows 5 tap targets. After submitting, it shows whether a correction was applied and by how much.
- A check-in prompt card appears on the Home screen each morning until the user checks in.

### Goal completion and maintenance (FR-9)

- `GoalCompletionUseCase` transitions ACTIVE → COMPLETED on or after the target date, then COMPLETED → MAINTAINED on the next call.
- The `observeActive()` DAO query now matches ACTIVE, COMPLETED, and MAINTAINED, so the app continues to generate plans and show the goal after completion.
- Home shows a completion banner with an invitation to set a new goal, or a "Holding your schedule" card when maintained.

### Notification reminders (FR-10)

- `ReminderScheduler` uses `AlarmManager.setExactAndAllowWhileIdle` when `SCHEDULE_EXACT_ALARM` is granted, falling back to `setAndAllowWhileIdle`. Five reminder types: bright light, caffeine cutoff, bedtime (30 min early), morning check-in (wake + 15 min), and last meal (30 min early).
- `ReminderReceiver` handles the alarm, creates per-type notification channels, and offers a "Remind me in 30 min" snooze action on each notification.
- `ReminderType` enum maps each type to a request code base and channel ID.

### Background daily sync

- `DailyPlanWorker` (HiltWorker) runs once per day around 4 AM via WorkManager. It syncs Health Connect in the background (if `READ_HEALTH_DATA_IN_BACKGROUND` is granted), recomputes the model, checks goal completion, refreshes the plan, and schedules notifications.
- WorkManager's default initialization is disabled in the manifest; `CircaPinApp` provides the Hilt-aware `Configuration`.

### Progress screen (FR-7)

- `ProgressScreen` shows a shift card with a progress bar (day N of M), start/current/target DLMO, and a timeline of the last 14 DLMO snapshots.
- Goal status drives the display: active shows the progress bar, completed shows "Goal reached", maintained shows "Maintaining your schedule".
- Reachable from a "Progress" button on the Home goal card.

### Settings screen

- `SettingsScreen` has toggle switches for each reminder type (light, caffeine, bedtime, check-in default ON; meals OFF).
- Shows exact alarm permission status with a tap-to-grant link on Android 12+.
- Settings is the third bottom navigation tab.
- Preferences are stored in SharedPreferences with a callbackFlow observer.

### Integration

- `TodayUpdater` now also calls `GoalCompletionUseCase` and `ReminderScheduler.schedule()` after each plan refresh.
- `MainActivity` requests `POST_NOTIFICATIONS` permission on Android 13+ at launch.
- `HomeViewModel` exposes today's check-in and goal status; `HomeScreen` renders the check-in card and completion banners.

## New domain entities and contracts

- `CheckIn(date, alertness, predictedAlertness, appliedCorrectionMinutes)`
- `ReminderPreferences(lightReminder, caffeineReminder, bedtimeReminder, checkInReminder, mealReminder)`
- `ModelSnapshot(estimatedDlmo, sleepDebtHours, socialJetlagHours, confidence, capturedAt)` — domain wrapper over the existing entity
- `GoalStatus.MAINTAINED` added to the enum
- `GoalRepository.update()` added for goal status transitions
- `CheckInRepository`, `PreferencesRepository`, `SnapshotRepository` interfaces and implementations

## Tech stack

No new libraries. WorkManager and AlarmManager are Android framework. SharedPreferences backs the reminder toggle storage.

## Known issues and follow-ups

- **No Phase 4 tests yet.** ProcessCheckInUseCase, GoalCompletionUseCase, and the new ViewModels need unit tests. The existing 190 Phase 3 tests are unaffected.
- **ReminderReceiver snooze** creates `ReminderScheduler(context)` directly instead of through Hilt. Works because it only needs a Context, but fragile if dependencies grow.
- **Notification scheduling runs on every TodayUpdater emission**, not just when the plan changes. Harmless (alarms are idempotent via `FLAG_UPDATE_CURRENT`) but wasteful.
- **Check-in availability** is not time-gated — it shows all day, not just in the morning. A morning-only window could be added in Phase 5.
- **No deep link from the check-in notification** to the check-in screen. The notification just opens the app.

## Decisions

| Decision | Outcome |
|---|---|
| Fixed plans (no live update) | Kept from Phase 3 |
| Two-consecutive-mismatch rule for DLMO correction | Prevents single-day noise from shifting the model |
| MAINTAINED goals keep generating plans | User's schedule doesn't drift after reaching the goal |
| Exact alarms with inexact fallback | Best-effort timing without requiring the permission |
| Reminders default ON except meals | Matches user's stated preferences |
| POST_NOTIFICATIONS requested at launch | Simple; could move to a more contextual moment later |

**Next:** Phase 5 — UI polish, accessibility, and any remaining items from the outline.
