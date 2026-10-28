# IT Service Appointments

This is a solution to the [IT service management coding challenge](docs/task/README.de.md). It has a Spring Boot backend
and an Ext JS frontend. Customers can find and book 15-minute-grid appointments with IT service providers, and the app
never lets a provider's concurrent capacity be exceeded, even when requests come in at the same time. Legacy appointments
can be imported from CSV.

| | |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, executable JAR |
| Database | PostgreSQL (default, via `docker-compose.yml`) or H2 (profile `h2`, no setup) |
| Frontend | Ext JS 6.2 (GPL, classic toolkit), served by the backend at `http://127.0.0.1:8080/` |
| API | [`rest-api-spec.yml`](rest-api-spec.yml) (Swagger 2.0, original spec plus documented extensions) |
| Decisions | [`docs/adr/`](docs/adr) |
| Requirements → tests | [`docs/test-coverage.md`](docs/test-coverage.md) |
| AI usage | [`docs/ai-usage.md`](docs/ai-usage.md) |

## Prerequisites

- **JDK 21+** (a full JDK, since `javac` is needed to build). Maven isn't needed because the wrapper `./mvnw` is included.
- **Docker**, for PostgreSQL and for the integration tests (Testcontainers).

## Build and run

```bash
# 1. Start PostgreSQL (database/user/password: icares, port 5432)
docker compose up -d

# 2. Build the executable JAR (runs all tests; use -DskipTests to skip them)
./mvnw clean package

# 3. Start
java -jar target/appointments-1.0.0.jar
```

Then open **http://127.0.0.1:8080/**.

On startup, Flyway creates the schema and the provider/service catalog from `application.yml` is synced into the
database.

**Without Docker:** use the embedded H2 file database. Data goes to `./data/`.

```bash
./mvnw clean package -DskipTests
java -jar target/appointments-1.0.0.jar --spring.profiles.active=h2
```

Other database settings can be passed as usual, e.g. `--spring.datasource.url=jdbc:postgresql://host:5432/db`.

## Importing the legacy appointments

The provider is **always chosen explicitly**, either in the URL or in the UI's *Import CSV* tab. It is never taken from
the file name.

> ⚠️ **The two files in `termine/` have swapped names.** `Computer-Service-Schneider.csv` contains DIA/LAP/OSI, which is
> the IT-Service Müller catalog. `IT-Service-Mueller.csv` contains DIA/VIR/DAT, which is the Schneider catalog. Import
> them by content:

```bash
curl -F file=@termine/Computer-Service-Schneider.csv http://127.0.0.1:8080/it-services/IT-MUELLER/appointment-imports
curl -F file=@termine/IT-Service-Mueller.csv         http://127.0.0.1:8080/it-services/CS-SCHNEIDER/appointment-imports
```

Each import imports 20 of 20 rows. If you import a file into the provider named in its file name instead, the
foreign service codes are rejected and listed in the report. For example, the Schneider-named file into `CS-SCHNEIDER`
gives 11 rows imported (DIA) and 9 rejected (`UNKNOWN_SERVICE_CODE`: LAP, OSI), each with its line number. Importing
the same file again adds nothing: every row is reported as a `DUPLICATE`.

Rejection reasons: `MALFORMED_ROW`, `MISSING_FIELD`, `INVALID_DATE`, `OFF_GRID`, `OUTSIDE_OPENING_HOURS`,
`UNKNOWN_SERVICE_CODE`,
`CAPACITY_EXCEEDED`, `DUPLICATE`. Reports are stored and can be fetched again via
`GET /it-services/{id}/appointment-imports[/{importId}]`.

## API at a glance

`serviceId` in the spec means the **provider**, and `repairServiceId` means the **service code**. The spec's naming is
kept as-is.

```bash
H=http://127.0.0.1:8080
curl $H/it-services                                    # providers: IT-MUELLER, CS-SCHNEIDER
curl $H/it-services/IT-MUELLER/services                # DIA 30 min, LAP 120 min, OSI 60 min
curl "$H/it-services/IT-MUELLER/appointment-suggestions?repairServiceId=LAP&from=2026-11-02T08:00:00Z&to=2026-11-02T13:00:00Z"
curl -i -H 'Content-Type: application/json' \
     -d '{"customerNumber":"10001","start":"2026-11-02T09:45:00Z","repairServiceId":"LAP"}' \
     $H/it-services/IT-MUELLER/appointments           # 201 + Location; 409 when the slot is taken
curl "$H/it-services/IT-MUELLER/appointments?from=2026-11-02T00:00:00Z&to=2026-11-02T23:59:59Z&repairServiceId=LAP"
curl $H/it-services/IT-MUELLER/appointments/{appointmentId}
```

Errors are returned as RFC 9457 problem details: `400` for invalid input or an off-grid start, `404` for an unknown
provider, service or appointment, and `409` when there is no capacity left or the booking is a duplicate.

All times are UTC `Instant`s. Inputs may carry any offset and may omit seconds (`2026-11-02T10:00+01:00`,
`2026-11-02T08:00Z`). Outputs are always `…Z`. The UI shows and accepts UTC as well. Note that 08:00Z in November
is 09:00 in Germany (CET).

## Scheduling rules

- An appointment starts on a 15-minute boundary (`:00/:15/:30/:45`), and `end = start + duration of the provider's service`.
- Intervals are half-open, `[start, end)`. An appointment ending at 09:00 does not overlap one starting at 09:00.
- A slot is free when the **peak** number of appointments running in parallel during its **whole** duration
  stays below the capacity. Booked and imported appointments count the same way.
- New bookings must not start in the past (400), and suggestions never include past slots. Imported legacy
  appointments may be historic.
- Durations need not be multiples of 15 minutes. Only starts are on the grid, and the capacity check uses exact
  intervals.
- Appointments must lie completely within the provider's **opening hours** (local time, `Europe/Berlin`, with
  daylight saving handled). They may end exactly at closing time, but may not run past it or across midnight. Müller is
  open Mon–Fri 08:00–18:00 and Sat 09:00–13:00; Schneider Mon–Fri 08:00–17:00 and Sat 10:00–14:00. This is a
  documented deviation; see [ADR 0007](docs/adr/0007-opening-hours.md).
- Suggestions are every grid start in `[from, to]` whose service finishes by `to`, lies within opening hours and fits.
  The search range is limited to 31 days.
- Overbooking under concurrent requests is prevented with a row lock on the provider during the re-check and the
  insert, for both bookings and imports. The loser of a race gets 409. Waiting for the lock is capped at the same
  `appointments.lock-timeout-ms` (10 s) on PostgreSQL and H2. If the cap is reached, the request gets 503 +
  Retry-After. See [ADR 0003](docs/adr/0003-pessimistic-provider-lock.md).

## Adding a provider or service

Edit `catalog.providers` in [`application.yml`](src/main/resources/application.yml), or override it with an external
`application.yml` or environment variables, then restart. No code changes are needed.

```yaml
catalog:
  providers:
    - id: PC-HILFE-WEBER
      name: PC-Hilfe Weber
      capacity: 4
      time-zone: Europe/Berlin
      opening-hours:            # missing days are closed; omit entirely = open around the clock
        MONDAY: 09:00-17:00
        FRIDAY: 09:00-15:00
      services:
        - { code: DIA, name: PC-Diagnose, duration-minutes: 20 }
```

## Tests

```bash
./mvnw test     # 143 tests; integration tests start PostgreSQL 17 via Testcontainers (Docker required)
```

The **acceptance suite** (`acceptance/`, 55 tests) follows the task text scenario by scenario (C1–C13, G, E, S, B, K,
I, L, P, X1). The mapping and traceability matrix are in [`docs/test-coverage.md`](docs/test-coverage.md). The
remaining tests cover the design's edge cases:

| Test | Covers |
|---|---|
| `OpeningHoursTest` | Local opening hours, closed days, end exactly at closing, past closing, never across midnight, DST in spring and autumn |
| `CapacityCalculatorTest`, `SlotFinderTest`, `TimeGridTest` | Overlaps, touching intervals, a peak inside the duration, Müller (cap 2) and Schneider (cap 3) scenarios, grid rounding |
| `AppointmentApiTest` | Booking (201/400/404/409), the end time per provider, past starts, times without seconds, duplicates, suggestions, filters, details |
| `ConcurrentBookingTest` | 20 parallel bookings of one slot give exactly 2 created; overlapping parallel bookings; an import racing bookings; identical parallel requests; parallel re-imports |
| `CatalogExtensibilityTest` | A new provider and a changed duration via configuration only; existing appointments keep their end time |
| `H2ProfileTest` | The whole flow, including the row lock and waiting for it, on the H2 profile |
| `LockTimeoutTest` | A lock timeout returns 503 + Retry-After |
| `FrontendTest` | The Ext JS app and runtime are served from the JAR |
| `SpecContractTest` | Implemented endpoints and DTO fields match `rest-api-spec.yml` exactly |
| `ImportApiTest`, `CsvAppointmentParserTest` | Real legacy files, wrong-provider import, re-import, every rejection reason with its line number, header errors |

To check that the concurrency tests actually work, I removed the lock and ran them again:
- **Booking without the lock:** 10 of 20 parallel bookings succeeded at capacity 2, and the test failed.
- **Import without the lock:** the import-vs-booking race test and the parallel re-import test failed.

The mapping of every task requirement and known pitfall to its test is in
[`docs/test-coverage.md`](docs/test-coverage.md).

## Manual frontend check

The task requires a working frontend, not automated frontend tests. Run this check after `java -jar …` and
importing the two legacy files as shown above, at http://127.0.0.1:8080/. All times in the UI are UTC.

| # | Function (task) | Steps | Expected |
|---|---|---|---|
| 1 | Select a provider | *Book appointment* tab, open "IT service provider" | Müller (capacity 2) and Schneider (capacity 3) |
| 2 | Select a service | Choose Müller, open "Service" | DIA 30 min, LAP 120 min, OSI 60 min |
| 3 | Enter a period | From 2026-11-02 08:00, To 2026-11-02 13:00, service LAP | Fields accept the values |
| 4 | Show suggestions | "Find available appointments" | 09:45, 10:00, … 11:00. Nothing before 09:45, because the imported LAP 09:00 and DIA 09:15 fill capacity 2. |
| 5 | Book | Select 09:45, customer number `C-42`, "Book selected appointment" | Confirmation with end 11:45; the list refreshes; booking the same slot again shows "No capacity left" (409) |
| 6 | Provider's appointments in a table | *Provider appointments* tab, Müller, From/To 2026-11-02, service LAP, "Search" | Two rows: 10003 (IMPORTED) and C-42 (BOOKED) |
| 7 | Details of an appointment | Click a row | The details panel shows id, customer, start, end, provider, service and source |

Also check the *Import CSV* tab: importing `termine/IT-Service-Mueller.csv` into Müller lists rejected rows with line
number and reason (`UNKNOWN_SERVICE_CODE` for VIR/DAT).

This flow was last run via headless Chrome (see [`docs/ai-usage.md`](docs/ai-usage.md)).

## Project layout

```
src/main/java/de/challenge/appointments/
  scheduling/   pure domain: TimeSlot, TimeGrid, CapacityCalculator, SlotFinder (no Spring, no DB)
  catalog/      providers and services, YAML config, startup sync
  appointment/  Appointment entity, AppointmentScheduler (single write path), booking/suggestion/query services
  imports/      CSV parser, import batches, rejection reasons
  api/          REST controllers, DTOs, RFC 9457 error mapping
src/main/resources/
  db/migration/ Flyway schema
  static/       Ext JS app (index.html, app/) and the vendored Ext JS 6.2.0 GPL runtime
docs/adr/       architecture decision records
docs/ai/        implementation plan produced with AI assistance
```
