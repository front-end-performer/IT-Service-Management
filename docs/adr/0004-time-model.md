# 0004 – UTC instants, half-open intervals, peak-concurrency check

**Status:** accepted

## Context

The CSV dates carry a UTC offset (`2026-11-02T08:00Z`), and the API uses ISO-8601 `date-time`. Capacity must hold
"throughout the entire service duration", so checking only the start time is not enough.

## Decision

- **Time handling:** all times are `java.time.Instant`, stored as `timestamp with time zone`, with JDBC time zone set
  to UTC. Inputs may use any offset. Outputs are always `Z`. A CSV date without an offset is **rejected**
  (`INVALID_DATE`) rather than silently interpreted.
- **Grid:** the 15-minute grid is defined in UTC: a valid start has `epochSecond % 900 == 0` and no nanoseconds.
  This is identical to local quarter hours for all whole-hour and half-hour time zones, including Europe/Berlin.
- **Intervals** are half-open, `[start, end)`. Back-to-back appointments never overlap.
- **End time:** `end = start + duration` is computed by the server and **stored**. Overlap queries become
  `start < :to AND end > :from` on an index `(provider_id, start_at, end_at)`, and changing a configured duration
  does not move existing appointments.
- **Capacity check:** this is a sweep line over the start and end events of the appointments that overlap the
  candidate, with ends sorted before starts at equal instants. It yields the **peak** parallel load within the
  candidate, and the candidate fits if `peak < capacity`. This catches loads that build up after the candidate's
  start, and it does not over-count appointments that each overlap the candidate but not each other.
- **Suggestions:** the search rounds `from` up to the grid, steps in 15 minutes, keeps starts whose end is ≤ `to`,
  and loads the overlapping appointments once per search. The range is limited to 31 days.
- **Pure core:** the `scheduling` package has no Spring or JPA dependencies and is unit-tested in isolation.

## Consequences

- The UI explicitly shows and accepts UTC, which matches the API and the CSV data. Local-time display would be a pure
  frontend change.
- There are no opening hours, because the task doesn't define any. They would fit naturally as an additional filter in
  `SlotFinder` plus a check in `AppointmentScheduler`.
