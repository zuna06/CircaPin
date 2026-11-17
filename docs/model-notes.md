# Model assumptions and evidence

Reviewed November 7, 2026.

The homeostatic decay/recovery rates (0.0353/h and 0.381/h), lower/upper asymptotes (2.4 and 14.3), and circadian amplitude (2.5) match the default S/C equations in Åkerstedt, Folkard and Portin, [Predictions from the Three-Process Model of Alertness (2004)](https://cdn.craft.cloud/ca2895c5-8327-4824-8f06-e3438db1d6b7/assets/1.-Three-Process-Model-of-Alertness-Akerstedt-Folkard-Portin-2004.pdf), page A76. CircaPin implements a simplified S+C approximation; it omits sleep inertia and the paper's additional ultradian component and recovery-rate restriction. Its normalized percentages are not a validated clinical alertness scale.

The two-hour sleep-onset/DLMO interval is a population approximation, not a measurement of the individual. [Gooley et al. (2011)](https://pmc.ncbi.nlm.nih.gov/articles/PMC3047226/) observed a median interval near two hours under dim light, with substantial variability and a shorter interval under room light.

CircaPin-specific choices remain provisional: free-night weighting, recency weighting, chronotype mapping, sleep-day grouping, circadian peak offset, debt penalty, sleep-score weights, and check-in correction thresholds. Persistent feedback is bounded to three hours in either direction to prevent unlimited drift from repeated noisy ratings.

Automated validation covers synthetic phase histories, plan bounds, expected qualitative alertness patterns, feedback convergence on a constructed prediction error, and persistence through sync. These checks establish software behavior. They do not demonstrate 30-minute DLMO accuracy in people or establish that following a plan produces the predicted biological shift. That requires measured participant data.

Friday and Saturday remain the MVP free nights. Sleep duration defaults to eight hours and can now be changed in goal setup. Custom free nights remain deferred; sleep duration is not silently inferred from sparse or short sleep logs.

## Sleep-debt refinement (November 14, 2026)

Debt remains a 14-sleep-day estimate of logged duration shortfalls, with surplus sleep repaying existing debt but never creating a negative balance. Missing nights are not treated as zero sleep. This arithmetic is a tracking heuristic, not a validated model of biological recovery.

When stage information is available, debt now uses the session's estimated asleep fraction, excluding reported awake time. An eight-hour session with 75% asleep contributes six hours, creating two hours of deficit against an eight-hour need. Naps and recovery nights use the same accounting. Without valid stage information, the session duration remains the fallback.

Overlapping sessions are partitioned into non-overlapping intervals before estimating sleep. Known stage fractions take precedence over unstaged/manual duplicates. Where measured sources disagree, the highest known asleep fraction is used to avoid exaggerating debt; this is an explicit provisional conflict-resolution rule. Stage fractions describe the whole session, so this does not reconstruct exactly when awakenings happened. Phase estimation and sleep scoring retain their existing duration/timing inputs.

Sleep days now change at local noon, including DST transition days. Subtracting a fixed twelve elapsed hours could previously move that boundary to 11 AM or 1 PM. No schema migration is required; the next model recomputation uses the revised estimate.


## Personalized sleep and three-meal planning (November 17, 2026)

The physiological DLMO estimate still comes from chronotype, sleep history and check-in feedback. Daily planning additionally learns a behavioral bedtime from up to seven recent main sleep days within the preceding fourteen days. Naps, future records, stale records and overlapping duplicates do not get separate votes. At least three nights are needed before adjusting the model-derived bedtime. Circular offsets preserve midnight behavior; a median resists isolated late nights. The observed offset is limited to 90 minutes and blended by nights/7. This adjusts a behavioral schedule, not a measured melatonin onset.

The configured sleep duration is protected in full between planned bedtime and wake time, including elapsed duration over DST. Goal setup allows explicit duration changes; repeatedly logging short sleep does not lower the setting. Plans show a 30-minute onset window, a 45-minute wind-down lead, the full sleep period and the dated wake time. These are suggested planning ranges, not predictions of exact sleep onset.

Sparse records, confidence below 0.5, onset median absolute deviation above one hour, or debt of at least four hours limit daily shifts to 30 minutes. Otherwise the existing 60-minute advance/90-minute delay caps remain. A poor last-night score reduces the proposed step further and reachability is recomputed at that pace. The Today deadline warning uses the saved plan's shortfall. DLMO displayed in the body-clock section remains the physiological estimate and can differ from the behavioral schedule.

Today's completed main sleep wake anchors breakfast, light and nap timing when available; otherwise the habitual modeled schedule is used. Extremely late wake times cannot squeeze meals into zero-length windows: an eight-hour minimum waking interval is a scheduling guard, disclosed in the plan, rather than a biological claim about readiness for sleep.

The default is three meals. Breakfast starts 30 minutes after waking; dinner ends 2.5 hours before planned sleep. Breakfast and dinner windows are normally one hour, with a one-hour lunch window centered between the two anchors. For compressed waking intervals, widths shrink to at most one fifth of the anchor span so the three windows remain distinct. Meal windows are derived deterministically from the persisted first/last anchors, so reopening the plan preserves them. This is a timing template, not an inference about a user's diet or preferred meal times. Existing first/last meal reminders retain their settings; no new lunch notification is enabled.

Database version 3 preserves existing records and adds wake time and plan explanation fields. A version-2 daily plan has no wake field and is regenerated once when a current, post-sync model is available. Subsequent plans stay fixed for their day; changing the goal regenerates them. Exports identify database version 3.

The numerical blending rules, shift reductions and meal widths are provisional product heuristics. General guidance supports consistent sleep/wake and regular meal schedules and avoiding large meals close to bed; it does not validate these exact per-user windows. References: [NHLBI healthy sleep habits](https://www.nhlbi.nih.gov/health/sleep-deprivation/healthy-sleep-habits), [NHLBI insomnia treatment](https://www.nhlbi.nih.gov/health/insomnia/treatment).
