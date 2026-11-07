# Phase 1 Writeup: Foundation

Nov 6, 2026.

## Status

**All five Phase 1 items are built, and the deliverable works on an API 37 emulator:** the app reads sleep data from Health Connect and manual input, stores it locally, and shows a raw sleep log. Phase 1 is merged to `main`. **Not recorded here:** a Health Connect read on a real device.

## What was implemented and how

- **Modules:** `app → data → domain`, plus `buildSrc`. `domain` is pure Kotlin, so `app/di/AppModule.kt` constructs its classes.
- **Room:** 11 tables in `data/local`, mapped to `domain/entities`. Only `SleepRecord` and `UserProfile` have domain models so far; the rest get theirs in the phase that uses them.
- **Health Connect:** `HealthConnectSleepSource` is read-only and pages through 30 days of sessions. Re-syncing updates records by their Health Connect ID, so nothing is duplicated. Quality is the share of a session not spent awake, or empty without stage data.
- **Questionnaire:** five questions scored in `ChronotypeQuestionnaire`, giving a chronotype and a baseline DLMO from 8 PM to 1 AM. Onboarding saves the profile, then offers Health Connect or manual logging.
- **Sleep log:** a list of all records, with sync on launch, a Sync button, and a form for sleep or naps. Entries are rejected if they're in the wrong order, end in the future, run too long, or overlap a record.

## Tech stack and dependencies

| Area | Used in this phase |
|---|---|
| Toolchain | Gradle 9.8.1, AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.13, JDK 21 to build, Java 17 target |
| Android | minSdk 28, compile and target SDK 37 |
| `domain` | `kotlinx-datetime` 0.8.0, `kotlinx-coroutines-core` 1.11.0 |
| `data` | Room 2.8.5, Health Connect client 1.1.0 |
| `app` | Compose BOM 2026.09.00, Material 3, Navigation 2.10.2, Lifecycle 2.11.0, Hilt 2.60.1, AndroidX Hilt 1.4.0 |
| Testing | JUnit 5.14.4, Turbine 1.2.1, coroutines-test 1.11.0, Compose UI test, Espresso 3.7.0, AndroidX Test |

Vico and WorkManager are declared but not used yet.

## Verification

- **Automated:** 70 tests pass from a clean build with no warnings: 31 on the JVM and 39 on the emulator. The emulator tests cover all 11 tables and a real Health Connect write and read, including 1,100 sessions across pages.
- **By hand on the emulator:** the questionnaire, the real permission screen, sync, manual logging, the overlap error, relaunch without onboarding, and the disconnected banner, all with no crashes.
- **Not verified:** a real device, any API below 37 (including minSdk 28), and stage data from a real wearable.

## Issues and things to consider

**Likely to affect Phase 2**

- **Double counting:** the overlap check only covers manual entries. A manual night that a wearable later syncs is stored twice, which would inflate sleep debt.
- **Nap detection:** Health Connect has no nap flag, so any session up to 3 hours is a nap, including a very short night.
- **Chronotype numbers are provisional:** the score bands and DLMO range are my estimates, and the wording is original rather than the published MEQ. Validate them in Phase 2.

**Deferred or missing**

- **Background sync (FR-2):** sync only runs on launch and on Sync. A worker should also need `READ_HEALTH_DATA_IN_BACKGROUND` (untested for the app).
- **Goal setup (FR-1):** it isn't a Phase 1 task, so `UserProfile` target fields stay empty.
- **Deletion:** there's no delete in the UI yet. A deleted Health Connect record will return on the next sync, and sessions deleted at the source stay in the log.
- **Theme and icon:** there's no `ui/theme/` package or launcher icon yet.

**Outline corrections still needed**

- **Social jetlag example:** midnight-8 AM vs 3 AM-11 AM is 3 hours, not 1.5. Fix it before it becomes a Phase 2 fixture.
- **Min SDK:** the Health Connect client's minimum is API 26, so API 28 is a choice.
- **Permissions:** `POST_NOTIFICATIONS` is unlisted, exact alarms need a fallback, and WorkManager adds four permissions, including `FOREGROUND_SERVICE`.

## Decisions

**Applied:** all seven earlier decisions, except that the outline still needs FR-11 and FR-12 added to Phase 3. **Added beyond the outline:** `SleepRecord.externalId` for de-duplication, and `ScheduleGoal.createdAtMillis` to find a goal's starting phase.

| Still open | Recommendation |
|---|---|
| Handling a synced session that overlaps a manual one | Keep the Health Connect record and drop the manual one during sync. |
| Target wake time and date are in both `UserProfile` and `ScheduleGoal` | Keep them only in `ScheduleGoal`. |
| Where deleted Health Connect records are remembered | Keep a tombstone so sync skips them. Needed by Phase 5. |

**Next:** settle the overlap decision and start Phase 2.
