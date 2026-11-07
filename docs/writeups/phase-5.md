# Phase 5 Writeup: Polish & Data Controls

November 7, 2026.

## Implementation

- Settings exports the complete database and reminder preferences as versioned JSON or a ZIP containing a CSV for each table plus a manifest. CSV nulls use an unquoted `\N` marker so they remain distinguishable from empty strings. Android's share sheet grants access only to the selected export file.
- Sleep log entries can be deleted with confirmation. Synced deletions remain suppressed on later syncs. Sync also reconciles records removed from Health Connect within its 30-day read window.
- Delete all data stops foreground updaters, serializes against background updates and sync, cancels work and reminders, clears all database tables and local preferences, removes private export files, and returns to onboarding. It does not modify Health Connect or copies the user has already shared.
- Optional UsageStats access measures daily screen-on time and use after the plan's dimming time. Only daily totals are persisted. The home screen displays the totals. Foreground checks and a periodic worker can issue one early bedtime nudge per plan day after 30 minutes of screen use past dimming. Android may defer the worker; the app uses no persistent foreground service.
- Settings explains optional notification, usage, background-health and exact-alarm access. Background-health requests are feature-gated. Denial leaves manual logging and stored-data planning available. Notification permission is requested from its Settings explanation rather than at launch.
- First-meal and last-meal reminders have independent toggles. Screen dimming has its own reminder. Check-in notifications navigate to check-in. Alarm restoration handles reboot, package replacement, clock changes and exact-alarm access changes.
- Dynamic Material colors and system dark mode, a launcher icon, labeled reminder switches, stacked plan/progress values for large text, and shaded estimated sleep hours complete the visual changes. Expanding hourly forecast values animates its size. The forecast retains an hourly text alternative.
- Progress includes sleep debt, social jetlag, target date and the seven-day average sleep score. Its bar measures phase movement rather than elapsed calendar days. The timeline is scoped to the current goal.

## Phase 4 corrections included

Feedback corrections are persisted as model inputs, bounded to three hours in either direction and applied once per date. Repeated sync does not compound or discard them. Forecasts refresh from feedback and late sleep data; plan action times remain fixed. Afternoon naps now affect later alertness instead of being dropped by the last-night cutoff. Check-in is available between 4 AM and noon and handles missing forecasts and duplicate taps. Goal completion remains visible on the target date and moves to maintenance the following day. Screens follow plan-day and time-zone changes while open.

Cloud backup and device transfer explicitly exclude app data on both legacy and modern Android backup formats, in addition to `allowBackup=false`.

## Dependencies and validation

No new dependencies were added. The database remains at schema version 2. See `docs/mvp-verification.md` for actual build, test and device results, and `docs/model-notes.md` for the reviewed model assumptions and remaining limits.
