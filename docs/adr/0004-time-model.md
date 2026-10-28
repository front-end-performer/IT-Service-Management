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
- **Capacity check, peak concurrency:** this is a sweep line over the start and end events of the appointments that overlap the
  candidate, with ends sorted before starts at equal instants. It yields the **peak** parallel load within the
  candidate, and the candidate fits if `peak < capacity`. This catches loads that build up after the candidate's
  start, and it does not over-count appointments that each overlap the candidate but not each other.
  - This is equivalent to checking every 15-minute slot the candidate covers: load only changes at appointment
    boundaries, and those are exactly the events the sweep visits.
  - Unlike a slot table, it stays exact for any duration (see below).
- **Past:** new bookings must not start in the past (`400`), and suggestions start at
  `max(from, now)`. Imported legacy appointments may lie in the past, because they are history. "Now" comes from an
  injected `Clock`, and tests pin it to 2026-10-28T12:00Z so they don't break once the sample dates have passed.
- **Durations that are not multiples of 15 minutes:** these are supported without rounding. Only *starts* are bound
  to the grid. The capacity check works on exact intervals, not on 15-minute buckets, so a future 20-minute service
  (08:00–08:20) correctly blocks a start at 08:15 and frees one at 08:30. This is covered by `SlotFinderTest` and
  `CatalogExtensibilityTest`.
  - This yields the same results as "rounding the occupied slots up". Every other appointment also starts on the
    grid, so no grid start can fall between 08:20 and 08:30.
  - Validation at configuration time is therefore unnecessary, and the stored end time stays the real one.
- **Suggestions:** the search rounds `from` up to the grid, steps in 15 minutes, keeps starts whose end is ≤ `to`,
  and loads the overlapping appointments once per search. The range is limited to 31 days.
  - *Task says:* the spec only defines `to` as *"Ende des Suchzeitraums"*, and the task says *"Eingabe eines
    gewünschten Zeitraums"*. Whether `to` limits the start or the end is left open.
  - We read "end of the search period" as: the whole appointment lies within the period.
- **Pure core:** the `scheduling` package has no Spring or JPA dependencies and is unit-tested in isolation.

## Consequences

- **Time zones:** the UI explicitly shows and accepts UTC, which matches the API and the CSV data. Note that the
  providers are presumably German: since the switch to CET on 2026-10-25, `2026-11-02T08:00Z` is 09:00 local time.
  Showing local time would be a pure frontend change (format with `Europe/Berlin`), because the API stays UTC.
- **Opening hours** apply on top of these rules, per provider and in local time, with daylight-saving handling. See
  [ADR 0007](0007-opening-hours.md).
