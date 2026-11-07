# MVP completion and verification

November 7, 2026. This report supersedes the incomplete-work lists in the older Phase 4 handoff. Implementation is described in `docs/writeups/phase-5.md`.

## Coverage against the outline

| Requirement | Implemented behavior |
| --- | --- |
| FR-1 Onboarding | Chronotype questionnaire, goal setup, sleep permission rationale, manual fallback; optional permissions explained in Settings. |
| FR-2 Sleep ingestion | Foreground and daily background sync, feature-gated background permission, manual sleep/naps, source deletion reconciliation. |
| FR-3 Model | Phase, debt and social jetlag; persisted, bounded feedback corrections survive recomputation. |
| FR-4 Daily plan | Fixed daily action times, pacing caps, light/avoidance, caffeine, bedtime and conditional naps. |
| FR-5 Forecast | Hourly curve refreshes after feedback and late sleep data; shaded estimated sleep hours and a text alternative. |
| FR-6 Check-in | Five ratings, morning availability, one submission per date, consecutive-mismatch correction and notification navigation. |
| FR-7 Notifications | Bright light, dimming, caffeine, bedtime, check-in and independent first/last-meal reminders; toggles, snooze, exact/inexact paths and restoration. |
| FR-8 Progress | Start/current/target phase, phase movement, target date, goal-scoped history, debt, jetlag and weekly score average. |
| FR-9 Naps | Separate logging, debt recovery, forecast input and bounded nap recommendations. |
| FR-10 Data controls | Complete versioned JSON/CSV archive export through Android sharing; individual deletion and reset to onboarding. |
| FR-11 Sleep score | Duration/timing/fragmentation/consistency score, home display and weekly average. Deleting its source night clears a stale score. |
| FR-12 Eating windows | First/last-meal plan times and separate reminder toggles. |

The Phase 4 decisions retain a check-in reminder based on planned wake plus 15 minutes. Sleep scores are available once sleep data arrives rather than being hidden until a check-in. Daily action times remain fixed; the forecast now refreshes independently. These are explicit product choices rather than missing implementations.

## Verification results

All checks below completed on November 7, 2026.

- JVM: **151 passed** (111 domain, 33 app, 7 data), zero failures.
- Data instrumentation: **44 passed** on the API 37 emulator, including Room migration, Health Connect paging, repository operations and source deletions.
- App instrumentation: **39 passed** on a freshly restarted API 37 emulator, including UI, exports, reset, background worker, actual notification delivery/snooze, large text and dark mode. This is **234 distinct automated tests** with the JVM and data suites.
- The rebuilt final APK also passed five targeted integration checks for worker/reset, notifications and model performance. Exact alarms were explicitly tested with access allowed, and the inexact path passed with access denied. Test permission changes were restored afterward.
- `assembleDebug`, Android test APK compilation and `lintDebug` pass. Lint reports **0 errors and 11 advisory warnings** (toolchain update, pluralization heuristic, unused resources and KTX suggestions).
- The 30-day model benchmark, after warmup over 50 runs, recorded a median **2.6 ms** and p95 **10.0 ms** on the emulator, below the 200 ms computation target. This does not certify performance on a physical mid-range phone.
- Final screenshots were inspected in light/dark mode and at 200% text size. The exact-alarm explanation now keeps a readable width; the forecast displays all 24 hours, sleep shading, markers and a text alternative.

Earlier repeated emulator runs hit PixelCopy and activity-teardown timeouts. Android screenshot capture and an emulator restart produced the successful full run; no app assertions were disabled.

Reproducible build commands:

```powershell
.\gradlew.bat :domain:test :app:testDebugUnitTest :data:testDebugUnitTest
.\gradlew.bat :data:connectedDebugAndroidTest
.\gradlew.bat assembleDebug :app:assembleDebugAndroidTest lintDebug
```

The final app suite was run through `adb shell am instrument -w -r com.circapin.test/androidx.test.runner.AndroidJUnitRunner` with notification permission enabled. Its successful transcript is `build/verification/final-instrumentation.txt`; targeted final-APK and fallback transcripts are `final-integration.txt` and `inexact-fallback.txt` in that directory. These contain `OK (39 tests)`, `OK (5 tests)` and `OK (1 test)` respectively. Earlier Gradle app-test HTML reports retain the prior emulator timeout; the final direct instrumentation transcript is the authoritative app-suite result.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Test reports and visual captures remain in ignored build directories. The APK excludes app data from cloud backup and device transfers, in addition to disabling automatic backup. [Android documents why explicit extraction rules matter on modern devices](https://developer.android.com/identity/data/autobackup).

## Limits requiring a real-world pass

- Only an API 37 emulator image is installed. API 28–36 and a physical phone/wearable still need compatibility testing.
- Long-term battery drain, real-device launch latency, sustained chart frame pacing and OEM background restrictions need device measurement. Emulator tests cannot certify the outline's hardware budgets.
- Model tests use synthetic histories and constructed feedback errors. Measured DLMO accuracy and biological response to the plans have not been established; see `docs/model-notes.md`.
- A hands-on TalkBack walkthrough remains useful beyond the semantic labels, text alternatives and large-text checks in the automated suite.

Friday/Saturday free nights, the eight-hour sleep-need default and fixed daily action times remain deliberate MVP defaults from the writeups. Live action-plan updates and all features explicitly listed as post-MVP remain deferred.
