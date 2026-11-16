# Android follow-up verification

Verified the reminder and daily rollover changes on an isolated API 37.0 Google Play x86_64 emulator using the installed Android Studio SDK.

| Check | Result |
| --- | --- |
| Data instrumentation | 44 passed, zero failures/errors/skips |
| App core, worker/reset, reminders, progress/settings and visual checks | 17 passed |
| App home, goal setup, questionnaire and sleep log | 23 passed |
| Reminder suite with exact-alarm access denied | 4 passed (additional run of the same reminder tests) |

This covers 84 distinct Android tests. The four reminder tests passed with both exact-alarm access allowed and denied, including delivery and snooze. The new check-in regression verifies today's persisted meal/wake anchor, tomorrow's bounded fallback, and disabling both check-in alarm slots.

The first combined app run stopped after 19 passing tests when the emulator stopped responding to Android shell commands. No assertion failure was reported. Restarting the disposable emulator with 2 GB RAM and cameras disabled allowed both app groups to complete: `OK (23 tests)` and `OK (17 tests)`. No tests were disabled or assertions relaxed.

The existing AVD could not start because C: had about 140 MB free. The isolated AVD and writable test data were placed under the ignored `build/test-avds/` and `build/emulator-data/` directories on F:. The existing AVD configuration was preserved.

Local evidence (ignored build outputs):

- `data/build/outputs/androidTest-results/connected/debug/`: data JUnit XML.
- `build/verification-app-core.txt`: completed 17-test app group.
- `build/verification-ui-remaining.txt`: completed 23-test UI group.
- `build/verification-inexact-reminders.txt`: completed inexact-alarm reminder run.
- `build/verification-app-instrumentation.txt`: initial interrupted combined run.
- `build/device-visual-verification/`: captured screenshots. The dark large-text settings screen and dark forecast were visually inspected; no visible layout defects were found.

These emulator checks do not establish physical-device battery behavior, OEM scheduling reliability or wearable compatibility. The DST/time-zone/delayed-run timing cases are covered by the JVM regression tests; this Android run verifies app and reminder integration on API 37.

## WorkManager and sleep-debt follow-up — November 15, 2026

- All 162 JVM tests passed: 117 domain, 38 app and 7 data. Six new regressions cover stage-adjusted debt, overlapping/duplicate records, recovery nights, naps, invalid stage fractions and local-noon grouping across spring/fall DST.
- Five targeted API 37 instrumentation tests passed (`OK (5 tests)` in `build/verification-rollover-chain.txt`): three new chain tests plus the existing model performance and background-worker/reset checks. The full Android suite was not rerun for this change.
- The chain tests use the real persistent WorkManager instance, Hilt worker and Room database. They accelerate only the initial wait using `updateWork`, keeping the production work ID, unique chain and rollover input. They verify one successor, idempotent startup, retrying the same work after an injected database write failure, and replacement/rescheduling through the restore receiver for clock/time-zone changes. Time-zone changes are process-local and restored during cleanup.
- Model performance remained within the existing computation-budget assertion. Test APK compilation and `lintDebug` passed with zero lint errors.
- CI configuration and physical-device testing were left unchanged.
