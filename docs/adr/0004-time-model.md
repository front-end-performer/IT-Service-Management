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
- **Past:** new bookings must not start in the past (`400`), and suggestions start at
  `max(from, now)`. Imported legacy appointments may lie in the past, because they are history. "Now" comes from an
  injected `Clock`, and tests pin it to 2026-10-28T12:00Z so they don't break once the sample dates have passed.
- **Durations that are not multiples of 15 minutes:** these are supported without rounding. Only *starts* are bound
  to the grid. The capacity check works on exact intervals, not on 15-minute buckets, so a future 20-minute service
  (08:00–08:20) correctly blocks a start at 08:15 and frees one at 08:30. This is covered by `SlotFinderTest`.
- **Suggestions:** the search rounds `from` up to the grid, steps in 15 minutes, keeps starts whose end is ≤ `to`,
  and loads the overlapping appointments once per search. The range is limited to 31 days.
- **Pure core:** the `scheduling` package has no Spring or JPA dependencies and is unit-tested in isolation.

## Consequences

- **Time zones:** the UI explicitly shows and accepts UTC, which matches the API and the CSV data. Note that the
  providers are presumably German: since the switch to CET on 2026-10-25, `2026-11-02T08:00Z` is 09:00 local time.
  Showing local time would be a pure frontend change (format with `Europe/Berlin`), because the API stays UTC.
- **No opening hours:** the task doesn't define any, so suggestions cover the whole requested range, including night
  slots. This was an explicit decision during planning, to follow the spec literally. Opening hours would fit
  naturally as an optional per-provider setting in `catalog`, plus a filter in `SlotFinder` and a check in
  `AppointmentScheduler`.
