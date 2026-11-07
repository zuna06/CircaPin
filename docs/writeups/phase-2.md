# Phase 2 Writeup: The Model

Nov 7, 2026. Work is on the `phase-2` branch and not committed yet.

## Status

**All four Phase 2 items are built, and the deliverable is met:** given a sleep history, the model outputs a DLMO estimate, a sleep debt and a social jetlag score, and passes the outline's validation harness. It also runs in the app, recomputing whenever sleep data changes, and its estimate shows on the sleep log.

## What was implemented and how

- **Sleep days (`SleepDays`):** overlapping records are merged first, so a night reported by two apps counts once. Pieces less than 1 hour apart join into one bout, and bouts group into noon-to-noon sleep days. Each day's main sleep is its longest bout of at least 2 hours, whatever its nap flag, so a short night still counts.
- **Phase (`PhaseEstimator`):** DLMO is 2 hours before the weighted average onset of the last 14 days. Averages are taken on a 24-hour circle, so 11:30 PM and 12:30 AM average to midnight. Recent nights count more (2-day half-life), and Friday and Saturday nights count three times as much. The chronotype baseline acts as an extra night that fades as real nights arrive. Confidence is nights in the window divided by 7.
- **Debt (`SleepDebtCalculator`):** the running shortfall against sleep need over 14 days, never below zero, so sleep can't be banked. Unlogged days aren't charged, and naps pay debt back.
- **Social jetlag (`SocialJetlagDetector`):** free-night minus school-night sleep midpoint over 14 days. Zero until both kinds of night exist.
- **In the app:** `ModelUpdater` reruns `CircadianModel` whenever records or the profile change, including syncs, manual entries and replaced records. It stores the state, and adds a history snapshot only when the estimate changes.
- **Chronotype calibration:** the baseline range moved from 8 PM to 1 AM to 6:30 PM to 12:30 AM. Consistent answers now land within 45 minutes of the outline's rule (DLMO 2 hours before the chosen bedtime). That makes it consistent, not validated against measured melatonin.

**Also done before Phase 2:** a version 1 to 2 Room migration that moves schedule targets out of `UserProfile` and adds a table of deleted synced records, plus the outline PDF corrections.

## Tech stack and dependencies

No new libraries. The Room schema is now version 2, upgraded by an auto-migration, and both schema files are exported to `data/schemas`.

## Verification

- **Automated:** 125 tests pass with no warnings: 72 on the JVM and 53 on the API 37 emulator.
- **Validation harness:** a simulated 30-day shift from 3 AM to 11 PM onsets. The estimate only advances, trails the true phase by at most 60 minutes when the shift ends, and settles within 15 minutes of 9 PM.
- **Other model tests:** convergence within 10 minutes after 7 nights, nights either side of midnight, a daylight saving change, duplicate sources, and bounds checks across 200 randomised histories.
- **Migration:** a real version 1 database upgrades with its profile and sleep intact.
- **On the emulator:** 7 seeded nights starting at 12:30 AM gave melatonin onset 10:29 PM, debt 0.0 h and confidence 100%.
- **Not verified:** a real device, any API below 37, and accuracy for a real person. There's no measured melatonin data to compare against.

## Issues and things to consider

- **Every constant is provisional:** the 2-hour lead, 2-day half-life, three-times free-night weight, 1-hour gap and 2-hour main sleep. Tune them against check-ins in Phase 4.
- **The estimate lags a real shift**, by up to an hour at the end of a fast one. Phase 3 plans should allow for that.
- **Daylight saving nights shift midpoints:** the emulator showed −0.3 h of social jetlag because one Saturday night crossed the change.
- **Free nights are fixed** as Friday and Saturday, which won't fit students with no Monday or Friday classes.
- **FR-3 isn't fully done:** feedback corrections arrive with check-ins in Phase 4, and "your body thinks it's 1:30 AM" needs a goal to compare against, which comes in Phase 3.
- **The model only refreshes while the app runs.** A background job comes with notifications in Phase 4.
- **Sleep need** stays at 8 hours unless changed. Nothing personalises it yet.

## Decisions

| Open | Recommendation |
|---|---|
| Free nights fixed as Friday and Saturday | Keep for the MVP. Revisit with the semester planner. |
| Where the estimate is shown | Keep the sleep log card until the Phase 3 home screen replaces it. |

**Next:** commit and merge `phase-2`, then start Phase 3 with goal setup, since plans depend on a goal.
