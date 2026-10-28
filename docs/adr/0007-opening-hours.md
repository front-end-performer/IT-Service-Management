# 0007 – Opening hours per provider, in local time

**Status:** accepted. This supersedes the first version's "no opening hours" decision.

## Context

The task never defines opening hours. Without them, suggestions offer slots at 03:00 or on Sundays, which an IT
service shop cannot honour. The providers are German, while the API and the legacy data are in UTC. Germany switches
between CET (UTC+1) and CEST (UTC+2), so a fixed UTC window would shift by one hour twice a year.

The first version deliberately followed the spec literally and had no opening hours. A later review against a
checklist of common pitfalls made them a requirement, so they were added as a documented deviation.

## Decision

- **Configuration per provider:** each provider has a `time-zone` (e.g. `Europe/Berlin`) and `opening-hours`, one
  period per weekday as local wall-clock time.

  ```yaml
  time-zone: Europe/Berlin
  opening-hours:
    MONDAY: 08:00-18:00
    SATURDAY: 09:00-13:00
  ```

  - Days that are not listed are closed.
  - A provider without `opening-hours` is open around the clock. This keeps the configuration of simple or
    non-local providers short.
  - Invalid zones or periods (e.g. `18:00-08:00`) stop the application at startup.
- **Configured hours:**

  | Provider | Mon–Fri | Sat | Sun |
  |---|---|---|---|
  | IT-Service Müller | 08:00–18:00 | 09:00–13:00 | closed |
  | Computer-Service Schneider | 08:00–17:00 | 10:00–14:00 | closed |

  All 40 legacy appointments fall within these hours. The latest one ends at 16:00 local time.
- **Persistence:** the hours are stored like the rest of the catalog: `provider.time_zone` plus
  `provider_opening_hours (provider_id, day_of_week, opens_at time, closes_at time)` (Flyway `V2`).
  - These are the only non-instant time columns, and that is deliberate. "08:00 in Berlin" is a wall-clock rule, not
    a point in time.
- **The whole appointment must fit:** it must start at or after opening and end at or before closing *of the same
  local day*.
  - An appointment may end exactly at closing time (half-open interval).
  - It may **not** run past closing time, and it may **not** cross midnight, even if the next day is open. A repair
    is not interrupted overnight.
- **Daylight saving time:** each candidate's start and end are converted to `ZonedDateTime` in the provider's zone and
  compared as local times. Opening at 08:00 local is therefore 07:00Z in winter and 06:00Z in summer, on every day,
  including the days after the switch.
  - The non-existent hour (02:00–03:00 in spring) and the repeated hour (02:00–03:00 in autumn) are outside any
    configured opening hours, so they need no special handling.
  - The 15-minute grid stays in UTC. For zones with whole-hour or half-hour offsets, it is identical to local quarter
    hours.
- **Enforced everywhere:**
  - in `AppointmentScheduler`, the single write path, for both bookings and imports;
  - in `SlotFinder`, for suggestions.

  Results:
  - A booking outside the opening hours is rejected with **400**, like an off-grid or past start: the request can
    never succeed, independent of the current load.
  - An import row outside the opening hours is rejected as `OUTSIDE_OPENING_HOURS`, because the scheduling rules
    apply to imported appointments too.

## Consequences

- Suggestions are realistic, and requests over a long range produce far fewer candidates.
- One period per day means a lunch break needs a list of periods per day. The `OpeningHours` type and the table would
  carry this with a small change.
- Public holidays are not modelled. They would be a list of closed dates per provider.
- The API exposes the hours as an addition to the provider (`timeZone`, `openingHours`; ADR 0006). The UI shows them
  and works in the provider's local time. Error messages also name them, e.g. "outside the opening hours of
  IT-MUELLER ({MONDAY=08:00-18:00, …} Europe/Berlin)".
