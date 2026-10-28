# 0006 – Deviations from and extensions to `rest-api-spec.yml`

**Status:** accepted

The spec is implemented as written: all paths, parameters, status codes and field names. Everything listed below is an
addition, and it is reflected in [`rest-api-spec.yml`](../../rest-api-spec.yml).

| Change | Reason |
|---|---|
| `POST/GET /it-services/{serviceId}/appointment-imports[/{importId}]` (multipart upload, import report) | The task requires a CSV import with traceable error handling, and the spec has no endpoint for it. The provider in the path makes the assignment unambiguous. |
| `appointment.source` (`BOOKED` / `IMPORTED`) | Staff can tell legacy appointments from new bookings. This is an additive, backwards-compatible field. |
| Error bodies as RFC 9457 `application/problem+json` (`problem` definition) | The spec defines status codes but no error body. Clients and the UI need the reason, e.g. "No capacity left for LAP at …". |
| `400` on `GET …/appointments` for an invalid `from`/`to` | Unparsable dates and `from > to` were otherwise undefined. |
| `400` on suggestions for a range over 31 days | Prevents unbounded computations. The status code was already specified. |
| `409` also for a duplicate booking (same customer, service and start) | The spec describes 409 as "Termin nicht verfügbar". A duplicate is a variant of that. |
| `Location` header on `201` | Standard REST practice. The body is unchanged. |

**Naming:** the spec calls the provider `serviceId` and the provider's service `repairServiceId`. This is kept for
compatibility. Internally the code uses `Provider` and `RepairService`, and the DTO mapping is the only place where the
two vocabularies meet (`api/Dtos.java`).

**Inclusive filters:** `from` and `to` on the appointment list are inclusive and select every appointment that is at
least partly running in the period (`end > from AND start ≤ to`). That's what staff usually mean by "appointments on
Monday".
