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
