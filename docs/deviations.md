# Deviations register

The task permits deviations from the specification *"where appropriate, provided that they are clearly documented
and justified"* (task text: *"…das von der SPezifikation abgewichen werden kann, sollte in diesen Fällen für die
Nachvollziehbarkeit gestattet sein."*). This page lists **every** place where the implementation differs from
[`task/README.de.md`](task/README.de.md) or from the original `rest-api-spec.yml` (first commit). Each entry gives the
original, the change, the reason, the ADR with the full rationale, and the test that pins it down.

The list of API changes was derived mechanically: the original and current `rest-api-spec.yml` were parsed and
compared. That comparison yields **only additions**. No path, parameter, field or status code of the original spec
was removed or changed in meaning. In the YAML, every addition is marked *"Erweiterung"*.
`SpecContractTest` (paths and fields) and `SpecConformance` (every status code and API header of every API test response) keep the YAML and the code in sync.

Entries fall into three groups:
- **A – API extensions** add something the spec does not have. Clients that only know the original spec are
  unaffected.
- **B – Additional status codes** cover situations the spec leaves undefined.
- **C – Interpretations** decide what the spec or task leave open. These are not deviations in the strict sense, but
  they are listed so the reading is traceable.

**D – Deviations from the task** are listed separately at the end.

## A – API extensions (additive)

| # | Original | Change | Why | ADR | Test |
|---|---|---|---|---|---|
| A1 | No import operation, although the task requires a CSV import | `POST /it-services/{serviceId}/appointment-imports` (multipart), `GET …/appointment-imports[/{importId}]`; definitions `importReport`, `importRejection` | The task requires the import and an unambiguous provider assignment. The provider in the path provides that assignment, and the report makes error handling traceable. | [0005](adr/0005-csv-import-policy.md), [0006](adr/0006-api-deviations.md) | `ImportAcceptanceTest` I1–I9, `ImportApiTest` |
| A2 | `appointment` has no origin | `appointment.source` = `BOOKED` / `IMPORTED` | Staff can tell legacy appointments from new bookings (traceability of imports). | [0006](adr/0006-api-deviations.md) | L1, `ImportApiTest` |
| A3 | Status codes without an error body | RFC 9457 `application/problem+json` body (`problem` definition) on every error | Clients and the UI need the reason, e.g. "No capacity left for LAP at …". | [0006](adr/0006-api-deviations.md) | `AppointmentApiTest.errorsAreProblemDetails` |
| A4 | `201` without headers | `Location` header on created appointments and imports | Standard REST practice. The body is unchanged. | [0006](adr/0006-api-deviations.md) | B1 |
| A5 | `GET …/appointments` returns a plain list | **Opt-in** paging: `page`, `size` (1–500), total in `X-Total-Count`. Without `page`/`size`, **all** appointments are returned, exactly as specified. | The list grows with every booking and import, so the UI must be able to page. Paging is opt-in so that a client following the original spec never silently receives a truncated list. | [0006](adr/0006-api-deviations.md) | `AppointmentApiTest.pagesThroughAppointmentsWithTotalCount` |
| A6 | `itService` = id, name, capacity | Additional `timeZone` and `openingHours` | The UI shows and accepts times in the provider's local time and explains missing slots. Needed because of D1. | [0006](adr/0006-api-deviations.md), [0007](adr/0007-opening-hours.md) | `AppointmentApiTest.exposesTimeZoneAndOpeningHoursOfEachProvider` |
| A7 | `appointmentRequest.customerNumber` has no length | `minLength: 1`, `maxLength: 64`, also enforced for CSV rows (`INVALID_CUSTOMER_NUMBER`) | Matches the storage column. Without the limit, a single overly long CSV value made the whole import fail with 409. Found while compiling this register, now rejected row by row. | [0005](adr/0005-csv-import-policy.md) | `rejectsBodiesWithMissingFields`, `ImportApiTest.rejectsATooLongCustomerNumberAsARowNotTheWholeFile` |
| A8 | No import exists, so no import outcome | `importReport.status` (`RUNNING`/`COMPLETED`/`FAILED`) and `failureMessage` | An aborted import must be recognisable as such and say where it stopped; re-importing is safe. | [0005](adr/0005-csv-import-policy.md) | `ChunkedImportTest.unexpectedFailureMarksTheImportFailedAndReimportIsSafe` |

## B – Additional status codes

| # | Situation (undefined in the spec) | Response | Why | ADR | Test |
|---|---|---|---|---|---|
| B1 | `GET …/appointments` with an unparsable date, `from > to`, or invalid `page`/`size` | `400` | Otherwise undefined. The spec has no `400` for this operation. | [0006](adr/0006-api-deviations.md) | `suggestionsValidateTheSearchRange`, `pagesThroughAppointmentsWithTotalCount` |
| B2 | Suggestion range over 31 days | `400` (already specified for an invalid range) | Prevents unbounded computations. | [0004](adr/0004-time-model.md) | `suggestionsValidateTheSearchRange` |
| B3 | Booking start in the past | `400` | A past slot can never be available. See D2. | [0004](adr/0004-time-model.md) | `rejectsBookingsInThePast` |
| B4 | Booking outside opening hours or past closing time | `400` | The request can never succeed, so it is not a `409`. See D1. | [0007](adr/0007-opening-hours.md) | `rejectsBookingsOutsideOpeningHoursOrPastClosing` |
| B5 | Same customer, service and start booked twice | `409` | A variant of "Termin nicht verfügbar". Protects against double clicks and retries. | [0006](adr/0006-api-deviations.md) | `sameCustomerServiceAndStartIsADuplicate`, `identicalParallelRequestsCreateExactlyOneAppointment` |
| B6 | Provider lock not obtained within `appointments.lock-timeout-ms` (10 s) | `503` + `Retry-After: 1` | A timeout says nothing about capacity (so not `409`), and it is retryable (so not `500`). | [0003](adr/0003-pessimistic-provider-lock.md) | `LockTimeoutTest` |
| B7 | CSV upload larger than 5 MB | `413` problem detail | Bounded request size. Verified against the packaged JAR (MockMvc does not enforce the limit). | [0006](adr/0006-api-deviations.md) | Manual check, see [ai-usage.md](ai-usage.md) |
| B8 | An import aborted by an unexpected error after it was recorded | `500` problem detail with `importId` and `Location` to the `FAILED` report. A lock timeout during an import gives `503` and is also recorded as `FAILED`. | The client learns that the import was aborted, where, and that re-importing is safe. Internal details stay in the server log. | [0005](adr/0005-csv-import-policy.md) | `ChunkedImportTest`, `LockTimeoutTest.importThatTimesOutIsAnsweredWith503AndRecordedAsFailed` |

## C – Interpretations of open points

| # | Open point (quote) | Our reading | Why | ADR | Test |
|---|---|---|---|---|---|
| C1 | Spec `to`: *"Ende des Suchzeitraums"* | A suggestion must **end** by `to` | The appointment lies within the period the customer entered. | [0004](adr/0004-time-model.md) | S1, S4 |
| C2 | Spec `from`/`to`: *"… inklusive"* | Every appointment **running** at some point of `[from, to]` | This is what staff mean by "appointments on Monday", including one already running at `from`. | [0006](adr/0006-api-deviations.md) | L2 |
| C3 | Task: *"Eindeutige Kennung"*. Spec example `id: DIA`, `code: DIA` | `repairServiceId` = code, unique **per provider**, always resolved with the provider in the path | Follows the spec example. It is the same key as the CSV, stable and readable. | [0002](adr/0002-configuration-driven-catalog.md) | B5, S9 |
| C4 | Task: *"…nachvollziehbar behandeln"* (no mode, no order) | **Partial** import in **file order**, in chunks of 100 rows | One bad row does not block the rest, and bookings are not blocked by large files. | [0005](adr/0005-csv-import-policy.md) | I3, I5, `ChunkedImportTest` |
| C5 | Task: the import must assign the file *"eindeutig"* | The provider comes from the request path, **never** from the file name | The sample files' names are swapped relative to their content. | [0005](adr/0005-csv-import-policy.md) | `importingIntoTheProviderNamedInTheFileNameRejectsForeignServiceCodes` |
| C6 | Spec examples `09:00:00Z`, CSV `08:00Z` | Inputs with or without seconds and with any offset; outputs always UTC `…Z` | Both formats occur in the original material. | [0006](adr/0006-api-deviations.md) | `acceptsTimesWithoutSeconds`, `acceptsStartWithOffsetAndStoresUtc` |

## D – Deviations from the task

| # | Task says | Change | Why | ADR | Test |
|---|---|---|---|---|---|
| D1 | No opening hours defined | Opening hours **per provider**, in local time (`Europe/Berlin`) with daylight saving handled. An appointment may not run past closing time or across midnight. Applies to suggestions, bookings and imports. | Without them, slots at 03:00 or on Sundays are offered. The configured hours contain all 40 legacy appointments. | [0007](adr/0007-opening-hours.md) | `OpeningHoursTest`, `suggestionsFollowEachProvidersOpeningHours`, `rejectsImportedAppointmentsOutsideOpeningHours` |
| D2 | Nothing about past dates | No suggestions or bookings in the past. Imports may be historic. | A past slot can never be offered. Legacy appointments are history. | [0004](adr/0004-time-model.md) | `rejectsBookingsInThePast`, `neverSuggestsPastSlots` |
| D3 | `docker-compose.yml` with `postgres:11-alpine`, port `5432` | `postgres:17-alpine`, port bound to `127.0.0.1` only. Database name, user and password unchanged. | PostgreSQL 11 has been end-of-life since November 2023. The database should not be reachable from the network. | [0001](adr/0001-technology-stack.md) | All integration tests run on PostgreSQL 17 |

Nothing else differs: all paths, parameters, field names, required fields and status codes of the original spec
behave as specified. This is enforced by `SpecContractTest`, `SpecConformance` and the acceptance suite.
