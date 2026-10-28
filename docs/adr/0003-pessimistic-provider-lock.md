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

### Waiting for the lock: identical on PostgreSQL and H2

- **Different defaults:** PostgreSQL waits for a row lock indefinitely, while H2 gives up after about 2 seconds
  (`Timeout trying to lock table`). This was measured with a test that held the lock for 5 s.
- **One explicit limit:** both databases therefore get the same limit, `appointments.lock-timeout-ms` (default
  10 s). It is set on every pooled connection via `spring.datasource.hikari.connection-init-sql`, using each
  database's own syntax: `SET lock_timeout = '…ms'` in PostgreSQL, `SET LOCK_TIMEOUT …` in H2.
- **When the limit is hit,** the request gets **503** with `Retry-After: 1`, not a 500, and not a 409 either. A
  timeout says nothing about capacity: the slot may well be free. Under normal load this never happens, because a
  booking holds the lock for milliseconds.

## Consequences

- Correct across several application instances, because the lock lives in the database. Works on PostgreSQL and H2.
- Bookings for the **same provider** are serialized. A booking takes milliseconds, so throughput per provider is far
  beyond what an IT service shop needs. Different providers don't block each other.
- **Tests on both databases** check the behaviour, and each one was confirmed to fail when the lock is removed:
  - `ConcurrentBookingTest` (PostgreSQL): parallel bookings, an import racing bookings, identical requests, parallel
    re-imports. Without the lock, 10 of 20 parallel bookings got through at capacity 2.
  - `ConcurrentBookingTest.bookingAndImportWaitForTheProviderLockInsteadOfFailing`: while another transaction holds
    the lock, a booking and an import **wait** for it. This proves they really take the lock. The opposite case,
    `otherProvidersAreNotBlocked`, shows a booking at another provider does not wait.
  - `AppointmentApiTest.parallelHttpRequestsGet201UntilCapacityThen409`: at HTTP level, the losers of the race get
    **409** and exactly as many requests as the capacity get 201.
  - `H2ProfileTest`: the same parallel-booking and lock-wait checks on H2. Without the lock, H2 overbooked too (3 at
    capacity 2).
  - `LockTimeoutTest`: a lock timeout becomes 503 + Retry-After.
- The unique key `(provider, service, customer, start)` is a second safety net against duplicate submissions. It maps
  to 409.
