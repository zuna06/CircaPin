# Personalized schedules verification

Implemented on `codex/personalized-schedules`.

## Behavior

- Recent main sleep records tune the model-derived bedtime; one-off late nights, duplicate sessions, naps and future records do not dictate the routine.
- Goal setup exposes the protected nightly sleep duration. Default: eight hours. Logging short sleep does not lower it.
- Today shows a 30-minute onset range, wind-down time, full sleep period, dated wake time, and the planned shift explanation.
- Three meals per day by default: breakfast, lunch and dinner each have a separate window anchored to the user's waking day. Meal names also appear in Next up.
- Sparse/irregular history, low confidence or substantial debt reduce shift pace. Deadline warnings use the stored plan's reduced-pace shortfall.
- Database 2-to-3 migration preserves existing data. Old-format plans refresh once after a current post-sync model is available; then daily action times remain fixed.
- Saved wake time is used consistently by the energy forecast and tomorrow's check-in reminder fallback.

See [model notes](model-notes.md) for exact heuristics, assumptions and references. These changes personalize planning behavior; they do not establish clinical accuracy or infer food preferences from uncollected data.

## Checks

- 174 JVM tests passed: 128 domain, 39 app, 7 data.
- Debug app, both instrumentation APKs and lint build passed. App lint: zero errors and ten existing advisory warnings; data lint: no issues.
- Seven emulator database checks passed: both migration tests and plan repository round trips, including the new sleep metadata.
- Eighteen emulator app checks passed: Home, goal setup including sleep-duration controls, visual checks, the worker/reset flow, and data export/delete controls.
- Final meal/sleep screenshots and normal/doubled-text results are under `build/personalized-review/` (ignored build outputs).

Evidence: `build/personalized-review/data-tests.txt`, `app-tests.txt`, `visual-normal.txt`, `visual-large-text.txt`; module XML results under `*/build/test-results/`.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
