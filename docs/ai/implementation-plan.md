> **Note:** This is the plan as approved before implementation (written with Claude Code in plan mode).
> It differs from the result in these places:
> - **Spring Boot version:** 4.1.2 instead of 3.5.x, because Spring Initializr no longer offers 3.5.
> - **Startup import:** the optional `import.on-startup` was dropped as unnecessary (YAGNI). Use the REST endpoint or the UI instead.
> - **Spec copy:** there is no copy under `static/`. The single source of truth is `rest-api-spec.yml` in the project root.

# Plan: IT Service Appointment Scheduling (coding challenge)

## Context
Greenfield project. Existing inputs only:
`README.md` (German task), `rest-api-spec.yml` (Swagger 2.0), `docker-compose.yml` (postgres:11-alpine, db/user/pw `icares`),
`termine/IT-Service-Mueller.csv`, `termine/Computer-Service-Schneider.csv` (20 rows each).
Goal: Spring Boot backend (executable JAR) + Ext JS frontend for finding/booking 15-min-grid appointments without
ever exceeding a provider's concurrent capacity, including under concurrent requests, plus CSV legacy import.

Toolchain on machine: Java 21 (use it), Node 24, Docker. No Maven installed → commit Maven wrapper (`mvnw`).

**Finding (trap in the data):** the CSV file names are swapped relative to content.
`Computer-Service-Schneider.csv` contains DIA/LAP/OSI (= Müller's catalog); `IT-Service-Mueller.csv` contains
DIA/VIR/DAT (= Schneider's). Consequence: the provider is never inferred from the file name — the import request
names the provider explicitly, and every row with an unknown service code is rejected and reported.
Importing a file into the "wrong" provider therefore produces a visible report (e.g. 11 imported DIA, 9 rejected).
Document this in README + ADR.

## Decisions (agreed)
- Maven, Spring Boot 3.5.x, Java 21, Spring Data JPA, Flyway, Bean Validation.
- PostgreSQL default (docker-compose; bump image to `postgres:17-alpine`, documented deviation); `h2` profile for quick run.
- No opening hours: any 15-min slot in `[from, to]` with capacity is valid (spec-literal). All times UTC `Instant`.
- Ext JS **6.2.0 GPL classic, vendored** into the JAR (`src/main/resources/static/vendor/extjs-6.2.0/`:
  `ext-all.js`, `classic/theme-triton/resources/**`, `packages/font-awesome` / `font-ext`). Source: npm `extjs-gpl@6.2.0`
  / cdnjs. Rationale (ADR): public `@sencha/ext` 7.x/8.x on npm is trial-licensed; 6.2.0 is the newest freely
  redistributable GPL build; no Sencha Cmd needed.

## Domain model (tables via Flyway `V1__schema.sql`)
- `provider(id varchar PK e.g. IT-MUELLER, name, capacity int >0)`
- `repair_service(id uuid/bigint PK, provider_id FK, code, name, duration_minutes >0, UNIQUE(provider_id, code))`
  — API `repairService.id` exposed as the code-scoped id string, e.g. `DIA` unique per provider (matches spec example).
- `appointment(id uuid PK, provider_id FK, repair_service_id FK, customer_number, start_at, end_at, source ENUM(BOOKED, IMPORTED), import_batch_id FK null, created_at)`,
  index `(provider_id, start_at, end_at)`; UNIQUE `(provider_id, repair_service_id, customer_number, start_at)` for import idempotency.
- `import_batch(id, provider_id, file_name, imported_at, total, imported, rejected)` +
  `import_rejection(batch_id, line_no, raw_line, reason)` → traceability of invalid rows.

Provider/service catalog is **configuration**: `application.yml` `catalog.providers[]` (id, name, capacity, services[code,name,durationMinutes])
bound to `@ConfigurationProperties`, upserted into DB at startup by `CatalogInitializer`. Adding a provider = config only.
End time is always computed `start + service.duration` (stored for querying, never accepted from clients).

## Core algorithm (`scheduling` package, pure Java, no Spring — heavily unit-tested)
- `CapacityCalculator.fits(existing intervals, candidate [s,e), capacity)`: half-open intervals; sweep line over
  start/end events of appointments overlapping the candidate → max concurrency within candidate < capacity.
  (End == next start is not an overlap.)
- `SlotFinder.suggest(from, to, duration, existing, capacity)`: round `from` up to 15-min grid, iterate `start` while
  `start + duration <= to`, keep slots where `fits`. Load existing once: appointments with `start < to && end > from`.
- Grid validation: `start` must have minute % 15 == 0 and zero seconds/nanos → else 400.
- Validation of search range: `from < to`, max range e.g. 31 days → 400.

## Concurrency (no overbooking)
`BookingService.book(providerId, request)` in one `@Transactional`:
1. `providerRepository.findByIdForUpdate(id)` with `@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE` on the provider
   row serializes all bookings/imports of that provider (works on Postgres and H2).
2. Load overlapping appointments, `CapacityCalculator.fits` → else `CapacityExceededException` → 409.
3. Insert. Commit releases lock.
Import uses the same lock + check per row. ADR documents alternatives (serializable isolation, Postgres exclusion
constraint – can't express capacity >1, optimistic version on provider).

## CSV import
- Endpoint (spec extension, documented): `POST /it-services/{serviceId}/appointment-imports` multipart `file`
  → `201` with `ImportReport {batchId, total, imported, duplicates, rejected[{line, raw, reason}]}`; `GET` list of batches.
- Parser: Apache Commons CSV, header required (`KdnNr,APP_DATE,SERVICE`), BOM/whitespace tolerant.
- Row reasons: `MISSING_FIELD`, `INVALID_DATE` (parse `2026-11-02T08:00Z` via `OffsetDateTime`/`Instant`),
  `OFF_GRID`, `UNKNOWN_SERVICE_CODE`, `CAPACITY_EXCEEDED`, `DUPLICATE` (already imported → skipped, not error).
- Row-level policy: valid rows are imported, bad rows rejected and persisted; whole file in one transaction under lock.
- Also optional startup import: `import.on-startup: [{providerId, path}]` (off by default) — README shows curl instead.

## REST API (follow `rest-api-spec.yml`; copy to `src/main/resources/static/rest-api-spec.yml`)
Controllers: `ProviderController` (`GET /it-services`, `/{id}`, `/{id}/services`), `AppointmentController`
(`GET/POST /{id}/appointments`, `GET /{id}/appointments/{appointmentId}`), `SuggestionController`, `ImportController`.
Filters on list: `from`, `to` (inclusive overlap), `repairServiceId`, `customerNumber` via JPA Specification.
DTOs as Java records matching spec names (`serviceId` = provider id, `repairServiceId` = service code — keep spec
naming, note confusion in docs). `@RestControllerAdvice` → RFC 7807 `ProblemDetail` for 400/404/409.
Spec deviations listed in `docs/api-deviations.md` and added to the yml (import endpoints, 400 on GET appointments for bad range).

## Frontend (Ext JS 6.2 classic, `src/main/resources/static/index.html` + `app/*.js`, no build)
`Ext.application` with a viewport, `tabpanel`:
- **Book tab**: provider combo (store `/it-services`) → service combo (`/it-services/{id}/services`) → from/to
  date+time fields → "Find slots" → grid of suggestions → select + customer number → "Book" (POST, 409 shows message,
  refreshes suggestions).
- **Appointments tab**: provider combo, filters (from/to, service), grid (start, end, customer, service, source),
  row select → details panel/window (`GET …/appointments/{id}`).
- **Import tab**: provider combo + filefield upload → report grid of rejections.
Served from the same JAR at `http://127.0.0.1:8080/`.

## Project layout
```
pom.xml, mvnw, docker-compose.yml, rest-api-spec.yml, README.md
src/main/java/de/.../appointments/
  catalog/ (Provider, RepairService, CatalogProperties, CatalogInitializer, repositories)
  scheduling/ (TimeGrid, CapacityCalculator, SlotFinder)  ← pure domain
  appointment/ (Appointment, AppointmentRepository, BookingService, SuggestionService, AppointmentQueryService)
  imports/ (CsvAppointmentParser, ImportService, ImportBatch, ImportRejection)
  api/ (controllers, dto records, GlobalExceptionHandler)
src/main/resources/ application.yml, application-h2.yml, db/migration/, static/
src/test/java/... + src/test/resources/csv/ (made-up fixtures)
docs/adr/0001…, docs/ai-usage.md
```

## Tests
- Unit: `CapacityCalculatorTest` (touching intervals, exact capacity, partial overlap mid-service, long service
  spanning several short ones), `SlotFinderTest` (grid rounding, end bound, Müller cap 2 / Schneider cap 3 examples),
  `TimeGridTest`, `CsvAppointmentParserTest` (bad date, missing column, unknown code, BOM).
- Integration (`@SpringBootTest` + Testcontainers Postgres): booking happy path, 409 on full, 400 off-grid,
  end time = start + duration, import of both real CSVs into correct/wrong provider → exact counts, re-import → all duplicates,
  suggestions exclude imported load.
- **Concurrency**: 20 threads via `CountDownLatch` book the same slot for capacity-2 provider → exactly 2×201, 18×409;
  plus DB assertion max concurrency ≤ capacity.
- MockMvc/WebMvc test for error JSON shapes.

## Docs / deliverables
- README: prerequisites, `docker compose up -d`, `./mvnw package`, `java -jar target/*.jar` (and `--spring.profiles.active=h2`),
  curl examples, import of the two CSVs (with the swapped-file note), test command.
- ADRs: 0001 stack & Ext JS 6.2 GPL vendoring, 0002 config-driven catalog, 0003 pessimistic provider lock,
  0004 half-open intervals & UTC, 0005 CSV row-level import policy, 0006 API deviations.
- `docs/ai-usage.md`: Claude Code used for planning/implementation/review; plan file, prompts/workflow, what was human-verified.
- `git init` + commits per milestone (only on request).

## Implementation order
1. Scaffold Maven project (Spring Initializr via curl), wrapper, compose bump, application configs.
2. Pure scheduling core + unit tests.
3. Entities, Flyway, catalog config + initializer.
4. Booking/suggestion/query services + lock, REST + error handling.
5. CSV import + reports.
6. Integration + concurrency tests.
7. Vendor Ext JS 6.2.0, build UI.
8. README, ADRs, AI-usage doc.

## Verification
- `./mvnw verify` (unit + Testcontainers ITs green).
- `docker compose up -d && ./mvnw package && java -jar target/appointments-*.jar`.
- curl: `GET /it-services` → 2 providers; import `termine/Computer-Service-Schneider.csv` into `IT-MUELLER` → 20 imported;
  `GET …/appointment-suggestions?repairServiceId=LAP&from=2026-11-02T08:00:00Z&to=2026-11-02T12:00:00Z` excludes saturated slots;
  POST booking twice into last free capacity → 201 then 409; parallel `xargs -P 10` curl burst → no overbooking.
- Open `http://127.0.0.1:8080/` and run the book / list / details / import flows by hand (or via the `run` skill + browser).
