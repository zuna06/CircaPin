# CircaPin

An Android app for local sleep tracking, estimated circadian phase, daily plans and optional reminders. Sleep can be entered manually or read from Health Connect. Estimates are provisional; see [model assumptions](docs/model-notes.md).

## Setup

1. Install JDK 21 and Android Studio with Android SDK Platform 37, Android SDK Build-Tools 36.0.0 and Platform-Tools. The app supports Android 9 (API 28) and later; compile/target SDK is 37 and bytecode targets Java 17.
2. Clone the repository. Open its root in Android Studio and select JDK 21 as the Gradle JDK. The checked-in Gradle wrapper downloads Gradle 9.8.1; no system Gradle is required.
3. Set `ANDROID_HOME` to your Android SDK directory, or create an untracked `local.properties` with `sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk` (use your actual SDK location).
4. Allow network access on the first build to resolve plugins and dependencies from Google Maven, Maven Central and the Gradle Plugin Portal. If the default Gradle cache is not writable, set `GRADLE_USER_HOME` to a writable directory. Offline builds require a populated cache.

From PowerShell at the repository root:

```powershell
.\gradlew.bat :domain:test :app:testDebugUnitTest :data:testDebugUnitTest
.\gradlew.bat assembleDebug :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest lintDebug
.\gradlew.bat :app:installDebug
```

On macOS/Linux use `bash gradlew` instead of `.\gradlew.bat`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Unit-test and lint reports are under each module's `build/reports/` directory. Release signing is not configured; these commands produce debug artifacts.

## Run and verify

Create an API 37 emulator or connect an Android device with USB debugging enabled. Complete the questionnaire and goal setup. Health Connect is optional: grant read-sleep access when offered, or use manual sleep entry. Older supported devices may require the Health Connect app. Real wearable validation requires another app supplying sleep sessions to Health Connect.

Settings explains optional notification, exact-alarm, usage and background-health permissions. Grant notification access for notification delivery checks. Background Health Connect reads require supported background-health access; without it, background planning uses stored data.

With one emulator/device connected:

```powershell
.\gradlew.bat :data:connectedDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Notification tests may skip delivery assertions without notification permission. Exercise exact-alarm access both allowed and denied. Instrumentation tests can reset app data; use a test device/profile. See [verification history and device gaps](docs/mvp-verification.md) for prior results and remaining physical-device checks.

## Automation and timing

GitHub Actions runs JVM tests, compiles app/data instrumentation APKs, builds the debug app and runs Android lint on pushes and pull requests. Reports and the debug APK are uploaded as artifacts. Connected-device tests remain a separate local step; CI compilation does not certify their runtime behavior.

Daily planning uses a unique one-time work chain. Each successful run calculates the next local 4 AM, so DST and delayed execution do not accumulate a 24-hour drift. Reboot/clock/time-zone restoration rebuilds reminders and realigns the chain. WorkManager may still defer execution under battery or OEM restrictions; tomorrow's morning check-in fallback is scheduled in advance.

Check-in reminders use today's stored meal-plan wake anchor, with tonight's planned sleep used for tomorrow's fallback. Wake plus 15 minutes is bounded to 4 AM–11:59 AM; delayed delivery and snoozes outside the check-in window are suppressed. Daily action times otherwise remain fixed as described in [later considerations](docs/later-considerations.md).

## Project layout

- `domain`: entities, model calculations, planning use cases and pure JVM tests.
- `data`: Room persistence, Health Connect ingestion and repository implementations.
- `app`: Compose UI, dependency injection, workers, reminders and data controls.
- `buildSrc`: shared Android configuration and dependency versions.
- `docs`: implementation writeups, model assumptions and verification history.
