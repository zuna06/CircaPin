# Model assumptions and evidence

Reviewed November 7, 2026.

The homeostatic decay/recovery rates (0.0353/h and 0.381/h), lower/upper asymptotes (2.4 and 14.3), and circadian amplitude (2.5) match the default S/C equations in Åkerstedt, Folkard and Portin, [Predictions from the Three-Process Model of Alertness (2004)](https://cdn.craft.cloud/ca2895c5-8327-4824-8f06-e3438db1d6b7/assets/1.-Three-Process-Model-of-Alertness-Akerstedt-Folkard-Portin-2004.pdf), page A76. CircaPin implements a simplified S+C approximation; it omits sleep inertia and the paper's additional ultradian component and recovery-rate restriction. Its normalized percentages are not a validated clinical alertness scale.

The two-hour sleep-onset/DLMO interval is a population approximation, not a measurement of the individual. [Gooley et al. (2011)](https://pmc.ncbi.nlm.nih.gov/articles/PMC3047226/) observed a median interval near two hours under dim light, with substantial variability and a shorter interval under room light.

CircaPin-specific choices remain provisional: free-night weighting, recency weighting, chronotype mapping, sleep-day grouping, circadian peak offset, debt penalty, sleep-score weights, and check-in correction thresholds. Persistent feedback is bounded to three hours in either direction to prevent unlimited drift from repeated noisy ratings.

Automated validation covers synthetic phase histories, plan bounds, expected qualitative alertness patterns, feedback convergence on a constructed prediction error, and persistence through sync. These checks establish software behavior. They do not demonstrate 30-minute DLMO accuracy in people or establish that following a plan produces the predicted biological shift. That requires measured participant data.

Friday and Saturday remain the MVP free nights. Sleep need remains an eight-hour default. Custom free nights and personalized sleep need were deferred in the phase writeups; they are not silently inferred from sparse sleep logs.

## Sleep-debt refinement (November 14, 2026)

Debt remains a 14-sleep-day estimate of logged duration shortfalls, with surplus sleep repaying existing debt but never creating a negative balance. Missing nights are not treated as zero sleep. This arithmetic is a tracking heuristic, not a validated model of biological recovery.

When stage information is available, debt now uses the session's estimated asleep fraction, excluding reported awake time. An eight-hour session with 75% asleep contributes six hours, creating two hours of deficit against an eight-hour need. Naps and recovery nights use the same accounting. Without valid stage information, the session duration remains the fallback.

Overlapping sessions are partitioned into non-overlapping intervals before estimating sleep. Known stage fractions take precedence over unstaged/manual duplicates. Where measured sources disagree, the highest known asleep fraction is used to avoid exaggerating debt; this is an explicit provisional conflict-resolution rule. Stage fractions describe the whole session, so this does not reconstruct exactly when awakenings happened. Phase estimation and sleep scoring retain their existing duration/timing inputs.

Sleep days now change at local noon, including DST transition days. Subtracting a fixed twelve elapsed hours could previously move that boundary to 11 AM or 1 PM. No schema migration is required; the next model recomputation uses the revised estimate.
