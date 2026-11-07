# Phase 3 Writeup: Daily Plan & Energy Forecast

Nov 7, 2026. Phase 3 is merged to `main`.

## Status

**All Phase 3 items are built, and the deliverable is met:** the app shows today's plan and energy curve, and the plan adapts to the time left before the goal. This is the outline's proof-of-concept checkpoint: the app takes in sleep data, estimates the body clock, and turns it into a plan.

## What was implemented and how

- **Goal setup (FR-1):** a wake time, a start date and an optional reason. It follows the questionnaire in onboarding, and Home links to it. It previews the shift the goal needs and warns if it can't be reached in time.
- **Pacing (`ShiftPlanner`):** the target DLMO is the wake time minus sleep need minus 2 hours. Tonight's step is the remaining shift divided by the nights left, capped at 1 hour a night earlier or 1.5 hours later.
- **Daily plan (`GenerateDailyPlanUseCase`):**
  - Light follows the phase response curve. When moving earlier, morning light starts at waking but never before the temperature minimum (DLMO + 7.5 h), since light before it moves the clock later.
  - Caffeine stops 8 hours before bed.
  - A nap is offered only with 4 h or more of debt or a poor night, and never within 6 hours of bed.
  - Meals start 30 minutes after waking and end 2.5 hours before bed (FR-12).
  - A poor night halves that day's step.
- **Energy forecast (`ComputeEnergyForecastUseCase`):** a two-process model, after the Three-Process Model of Alertness. Sleep pressure is simulated over the last 3 days of records, plus a circadian rhythm timed from DLMO and a sleep debt penalty.
- **Sleep score (FR-11):** 0 to 100, from duration, timing against last night's plan, fragmentation and consistency, with a note on the weakest part.
- **Home and navigation:** bottom tabs for Today and Sleep log. Today shows the goal, any shortfall, the body clock ("your body thinks it's 8:57 AM"), the score, the plan and the forecast, with hourly values as a text alternative.

## How a day's plan is made

The plan is fixed for the day, as decided. It's generated on the first open after 4 AM, and only a goal change regenerates it. Because a fixed plan can't correct itself, it waits until the app has synced Health Connect and rerun the model. The app now syncs every time it comes to the foreground (FR-2), through one shared `SleepSync`.

## Tech stack and dependencies

No new libraries. Vico (already declared) now draws the forecast. There's no schema change: Phase 1's tables held everything.

## Verification

- **Automated:** 190 tests pass with no warnings: 124 on the JVM and 66 on the API 37 emulator.
  - **Plans:** bright light never falls before the temperature minimum, caffeine leaves under half by bedtime, bedtime never moves more than the cap (including a goal tomorrow), and naps stay out of the last 6 hours before bed.
  - **Forecast:** daytime beats the small hours, the peak falls in the afternoon, debt lowers every hour, and a later clock moves the peak later.
  - **Fixed plan:** it isn't rebuilt from stale or pre-sync data.
- **On the emulator, from a fresh install:** onboarding, goal setup, then Home. Seven seeded nights at 12:30 AM against a 7 AM goal gave 1 h 29 min behind, a 7-minute step, bedtime 12:22 AM and a forecast peak at 4 PM.
- **Not verified:** a real device, API below 37, plan quality for a real person, and a sleep score from real data (the seed has no night for the day tested).

## Issues found and fixed

- **No sync on launch:** Home replaced the sleep log as the first screen, and sync only ran on the sleep log. The first plan was built from the questionnaire alone and then kept all day. Sync now runs app-wide.
- **Light when holding steady:** on a day with no shift, light could start before the temperature minimum.
- **Forecast during sleep:** the small hours looked nearly as alert as midday, because pressure recovers during sleep. Sleep hours now show alertness as if awake.
- **The chart scrolled sideways**, hiding everything after 9 AM. It now fits the card.

## Issues and things to consider

- **Forecast step at waking:** the curve jumps where "if awake" gives way to "after sleeping". It's accurate, but may read oddly. Shading sleep hours would help.
- **Provisional constants:** every timing, threshold and score weight is a first guess, including the alertness parameters, which I haven't checked against the paper. Tune them with Phase 4 check-ins.
- **Goals never complete:** after the goal date, the plan keeps closing any remaining gap. Marking goals complete belongs with progress tracking in Phase 4.
- **Late data:** sleep that reaches Health Connect after the plan is made doesn't change it. See `docs/later-considerations.md`.
- **Score timing:** it appears once last night syncs, not after a check-in as FR-11 says, since check-ins come in Phase 4.

## Decisions

**Made:** hard goals shift at the cap with a warning, navigation uses bottom tabs, plans stay fixed per day (live updates noted for later), and the forecast uses the two-process model.

| Open | Recommendation |
|---|---|
| Shade sleep hours on the forecast | Yes, in Phase 5's UI polish. |
| Mark goals complete after the date | Yes, with the Phase 4 progress screen. |

**Next:** start Phase 4 on its own branch.
