# 0006 – Deviations from and extensions to `rest-api-spec.yml`

**Status:** accepted

The spec is implemented as written: all paths, parameters, status codes and field names. Everything listed below is an
addition, and it is reflected in [`rest-api-spec.yml`](../../rest-api-spec.yml) and marked "Erweiterung" there.

The complete register of deviations, including task-level deviations, interpretations and the test for each, is in
[`docs/deviations.md`](../deviations.md). This ADR holds the API rationale.

| Change | Reason |
|---|---|
| `POST/GET /it-services/{serviceId}/appointment-imports[/{importId}]` (multipart upload, import report) | The task requires *"Die Anwendung muss bestehende Termine aus CSV-Dateien importieren können"*, and the spec has no endpoint for it. The task permits deviations: *"…das von der SPezifikation abgewichen werden kann, sollte in diesen Fällen für die Nachvollziehbarkeit gestattet sein."* The provider in the path satisfies *"Die Zuordnung der CSV-Datei zum jeweiligen IT-Service-Anbieter muss beim Import eindeutig sein."* |
| `appointment.source` (`BOOKED` / `IMPORTED`) | Staff can tell legacy appointments from new bookings. This is an additive, backwards-compatible field. |
| Error bodies as RFC 9457 `application/problem+json` (`problem` definition) | The spec defines status codes but no error body. Clients and the UI need the reason, e.g. "No capacity left for LAP at …". |
| `400` on `GET …/appointments` for an invalid `from`/`to` | Unparsable dates and `from > to` were otherwise undefined. |
| `400` on suggestions for a range over 31 days | Prevents unbounded computations. The status code was already specified. |
| `400` for a booking outside the provider's opening hours (or past closing time); no suggestions outside them | Opening hours are a documented deviation, see [ADR 0007](0007-opening-hours.md). The request can never succeed, so 400 rather than 409. |
| `400` for a booking start in the past | Not specified. A past slot can never be "available". |
| `409` also for a duplicate booking (same customer, service and start) | The spec describes 409 as "Termin nicht verfügbar". A duplicate is a variant of that. |
| `503` + `Retry-After` on booking/import when the provider lock is not obtained within `appointments.lock-timeout-ms` | Not specified. A timeout is no statement about availability, so not 409. It is retryable, unlike a 500. See ADR 0003. |
| `Location` header on `201` | Standard REST practice. The body is unchanged. |
| **Opt-in** `page`/`size` on `GET …/appointments` (size 1–500; only `page` given means size 100), total in `X-Total-Count` | The list grows with every booking and import, so the UI must be able to page. Without `page`/`size` **all** appointments are returned, exactly as specified, so a client that knows only the original spec never silently receives a truncated list. (A first version paged by default; that was changed for this reason.) |
| `customerNumber` limited to 1–64 characters (bookings: 400; CSV rows: `INVALID_CUSTOMER_NUMBER`) | Matches the storage column. Before the CSV check, one overly long value made a whole import fail with 409. |
| `413` problem detail for a CSV upload over 5 MB | Bounded request size. Verified against the packaged JAR. |
| `itService.timeZone` and `itService.openingHours` | Clients need the provider's zone to show and accept local times, and the opening hours to explain why slots are missing. These are additive fields. |

**Naming:** the spec calls the provider `serviceId` and the provider's service `repairServiceId`. This is kept for
compatibility. Internally the code uses `Provider` and `ServiceOffering`, and the DTO mapping is the only place where the
two vocabularies meet (`api/Dtos.java`).

**Time formats:** inputs accept seconds or no seconds, and any offset (`2026-11-02T09:00:00Z`, `2026-11-02T08:00Z`,
`2026-11-02T10:00+01:00`). Outputs are always UTC with `Z`.

**Contract check:** the YAML is the source of truth, and code is not generated from it. Instead, `SpecContractTest`
fails the build if:
- any implemented endpoint is missing from the spec, or vice versa, or
- any DTO's JSON fields differ from the matching definition's properties.

(springdoc would generate OpenAPI 3, while the spec is Swagger 2.0.)

**Inclusive filters:** the spec only says *"Beginn des Abfragezeitraums inklusive"* / *"Ende des Abfragezeitraums
inklusive"*, not inclusive of what. `from` and `to` on the appointment list are inclusive and select every
appointment that is at least partly running in the period (`end > from AND start ≤ to`). That's what staff usually mean by "appointments on
Monday".
