# Model assumptions and evidence

Reviewed November 7, 2026.

The homeostatic decay/recovery rates (0.0353/h and 0.381/h), lower/upper asymptotes (2.4 and 14.3), and circadian amplitude (2.5) match the default S/C equations in Åkerstedt, Folkard and Portin, [Predictions from the Three-Process Model of Alertness (2004)](https://cdn.craft.cloud/ca2895c5-8327-4824-8f06-e3438db1d6b7/assets/1.-Three-Process-Model-of-Alertness-Akerstedt-Folkard-Portin-2004.pdf), page A76. CircaPin implements a simplified S+C approximation; it omits sleep inertia and the paper's additional ultradian component and recovery-rate restriction. Its normalized percentages are not a validated clinical alertness scale.

The two-hour sleep-onset/DLMO interval is a population approximation, not a measurement of the individual. [Gooley et al. (2011)](https://pmc.ncbi.nlm.nih.gov/articles/PMC3047226/) observed a median interval near two hours under dim light, with substantial variability and a shorter interval under room light.

CircaPin-specific choices remain provisional: free-night weighting, recency weighting, chronotype mapping, sleep-day grouping, circadian peak offset, debt penalty, sleep-score weights, and check-in correction thresholds. Persistent feedback is bounded to three hours in either direction to prevent unlimited drift from repeated noisy ratings.

Automated validation covers synthetic phase histories, plan bounds, expected qualitative alertness patterns, feedback convergence on a constructed prediction error, and persistence through sync. These checks establish software behavior. They do not demonstrate 30-minute DLMO accuracy in people or establish that following a plan produces the predicted biological shift. That requires measured participant data.

Friday and Saturday remain the MVP free nights. Sleep need remains an eight-hour default. Custom free nights and personalized sleep need were deferred in the phase writeups; they are not silently inferred from sparse sleep logs.
