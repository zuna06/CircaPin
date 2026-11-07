# Later considerations

Ideas that came up during development and were deliberately left out for now, with enough context to revisit them.

## Update the daily plan live

**Now:** each day's action times (light, caffeine, bedtime, nap, meals) are generated once, on the first app open after 4 AM, and then stay fixed. Only a goal change regenerates action times. The forecast now refreshes when the model or sleep data changes, including after check-in. See `RefreshTodayUseCase`.

**Why it's fixed:** stable times are easier to follow, and Phase 4 will schedule notifications from the plan. Times that move during the day would make those notifications unreliable.

**The cost:** the plan waits for the app's own Health Connect sync on first open, but sleep that reaches Health Connect later, or is logged by hand after that, doesn't change today's action times. The most likely case is a wearable that syncs last night to Health Connect after the app was opened. The body clock card and the goal progress on the home screen do update live, so the plan and the estimate can briefly disagree.

**Options to weigh later:**

- Regenerate only the parts of the day that haven't happened yet, and leave past windows alone.
- Regenerate until the first plan item starts, then freeze.
- Let the user refresh the plan by hand.
- Regenerate whenever the estimate moves by more than a threshold, such as 15 minutes.

Any of these needs a rule for notifications already scheduled from the old plan.
