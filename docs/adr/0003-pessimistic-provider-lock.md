# 0003 – Overbooking is prevented by a pessimistic lock per provider

**Status:** accepted

## Context

Capacity is a property of a provider and a period: "at most N appointments in parallel at any time". Two concurrent
bookings can each read "N−1 running" and both insert. That is a classic write-skew anomaly, and `READ COMMITTED`
does not prevent it. A unique or exclusion constraint cannot express "at most N overlapping rows" for N > 1.

## Decision

Every write to a provider's schedule (booking and CSV import) runs in one transaction that:

1. loads the provider row with `SELECT … FOR UPDATE` (`ProviderRepository.findByIdForUpdate`,
   `@Lock(PESSIMISTIC_WRITE)`),
2. loads the provider's appointments overlapping the candidate interval and checks the peak concurrency
   (`CapacityCalculator`),
3. inserts the appointment and commits, which releases the lock.

All writes go through `AppointmentScheduler.schedule`, which is annotated `@Transactional(propagation = MANDATORY)`
so it cannot be called without a surrounding transaction. Suggestions are read without a lock. They are a snapshot,
and a slot taken in the meantime makes the booking fail with **409**.

### Alternatives considered

| Option | Why not |
|---|---|
| `SERIALIZABLE` isolation | Correct on PostgreSQL (SSI), but needs retry logic for serialization failures, and H2 behaves differently |
| Optimistic `@Version` on provider, bumped per booking | Equivalent serialization, but needs retries; contention per provider is low either way |
| PostgreSQL exclusion constraint | Only expresses capacity 1 |
| Counter table per 15-minute slot with row locks or check constraints | Works for capacity N, but adds a redundant data structure that must be kept in sync with durations |
| In-JVM `synchronized` or lock map | Breaks with more than one application instance |

## Consequences

- Correct across several application instances, because the lock lives in the database. Works on PostgreSQL and H2.
- Bookings for the **same provider** are serialized. A booking takes milliseconds, so throughput per provider is far
  beyond what an IT service shop needs. Different providers don't block each other.
- `ConcurrentBookingTest` verifies the behaviour against real PostgreSQL. With the lock replaced by a plain read,
  the same test let 10 of 20 parallel bookings through at capacity 2.
- The unique key `(provider, service, customer, start)` is a second safety net against duplicate submissions. It maps
  to 409.
