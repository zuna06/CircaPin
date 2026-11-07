# Phase 1 Writeup: Foundation

Nov 6, 2026.

## Status

**Phase 1 is not complete.** Only the multi-module Gradle project with Hilt wiring is done (app-level only, no bindings yet). Room entities and DAOs, the HealthConnect wrapper, the chronotype questionnaire and manual sleep/nap logging are not started, so the deliverable (read sleep data, store it, show a raw sleep log) is not met.

## What was implemented and how

- **Modules:** `app`, `domain`, `data` and `buildSrc`, with dependencies running `app → data → domain`. `data` exposes `domain` and the Room runtime through `api`, so `app/di` can provide the database.
- **Packages:** every package in the outline's tree exists under `com.circapin`. Empty ones hold a `.gitkeep`.
- **Build config:** the version catalog is `buildSrc/libs.versions.toml`, and SDK levels are in `AndroidConfig.kt`. JUnit Platform is on in all modules, and Room schemas export to `data/schemas`.
- **App shell:** `CircaPinApp` and `MainActivity` show the app name in Compose through Hilt. `allowBackup` is `false`.

## Tech stack and dependencies

| Area | Declared this phase (most not used by code yet) |
|---|---|
| Toolchain | Gradle 9.8.1 (wrapper), AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.13, JDK 21 to build, Java 17 target |
| Android | minSdk 28, compile and target SDK 37 |
| `domain`, `data` | `kotlinx-datetime` 0.8.0, `kotlinx-coroutines-core` 1.11.0, Room 2.8.5, Health Connect client 1.1.0 |
| `app` UI | Compose BOM 2026.09.00, Material 3, Activity 1.13.0, Core 1.19.1, Lifecycle 2.11.0, Navigation 2.10.2, Vico 3.3.1 |
| `app` DI, background | Hilt 2.60.1, AndroidX Hilt 1.4.0, WorkManager 2.12.0 |
| Testing | JUnit 5.14.4, Turbine 1.2.1, coroutines-test 1.11.0, Compose UI test, AndroidX Test, Room testing |

## Verification

- **Passed:** `assembleDebug`, `assembleDebugAndroidTest` and `test`, with no warnings on a clean compile. `domain` resolves only `kotlin-stdlib`, `kotlinx-datetime` and `kotlinx-coroutines-core`, and the merged manifest has no `INTERNET` permission.
- **Ran on an emulator (API 37):** the shell launches through Hilt with no crashes, in 2.5-2.8 s cold (debug build).
- **Not verified:** a real device or any API below 37, including minSdk 28. No tests exist, so `test` shows only that the setup works.

## Issues and things to consider

- **Social jetlag example is wrong:** midnight-8 AM vs 3 AM-11 AM is listed as about 1.5 hours, but the midpoints (4 AM, 7 AM) are 3 hours apart. Fix this before it becomes a Phase 2 test fixture.
- **Min SDK rationale is wrong:** the Health Connect client's minimum is API 26, so API 28 is a choice, not a requirement.
- **Missing permissions:** the daily WorkManager sync should need `READ_HEALTH_DATA_IN_BACKGROUND` per the Health Connect docs (untested). `POST_NOTIFICATIONS` is also unlisted, and exact alarms need a fallback because users can deny them.
- **Permissions added by libraries:** WorkManager adds four, including `FOREGROUND_SERVICE`, against "only the permissions it needs". Review before release, but keep `RECEIVE_BOOT_COMPLETED` so alarms survive a reboot.
- **Health Connect setup:** per the docs, it's a separate app install on Android 13 and below, and the permission flow needs a rationale screen declared in the manifest.
- **Theme, icon and insets:** the tree has no `ui/theme/` package, and there's no launcher icon. The shell handles edge-to-edge insets, and every new screen must too.

## Decisions made

All seven are settled. Only the coroutines change is applied so far, and the outline PDF still needs updating to match.

| Topic | Outcome |
|---|---|
| Room entities | They live in `data/local`, mapped to `domain/entities`, so `domain` stays free of Room. |
| Plans and goals | `DailyPlan` gets a `goalId` foreign key. |
| Phase history | Add a model snapshot table. `ModelState` stays as the current value. |
| Meal times | Only in `EatingWindow`, which has the manual override. Removed from `DailyPlan`. |
| Coroutines in `domain` | Allowed, and exposed through `api`. This changes the outline's "stdlib and kotlinx-datetime only" rule. |
| FR-11 and FR-12 | Both go in Phase 3, with the daily plan. |
| Package name | `com.circapin` is final. |

**Suggested order:** build Room and manual logging, the HealthConnect wrapper (real device), and the questionnaire.
