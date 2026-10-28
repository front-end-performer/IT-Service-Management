# 0005 – CSV import: explicit provider, row-level validation, traceable report

**Status:** accepted

## Context

Legacy files have the format `KdnNr,APP_DATE,SERVICE` and contain no provider. Each file "must be unambiguously
associated" with a provider, and "unknown service codes and invalid records" must be handled traceably. The two
sample files have **swapped names**: `Computer-Service-Schneider.csv` contains Müller's services, and vice versa.

*Task says:*
- *"Der Importprozess muss unbekannte Leistungscodes und ungültige Datensätze erkennen und nachvollziehbar
  behandeln."*
- *"Die Regeln gelten sowohl für neu gebuchte als auch für importierte Termine."*

The task does not say whether a file with bad rows is imported partially or not at all. It also does not say which
row wins when rows conflict. Both are therefore our decisions (below).

## Decision

- **Provider assignment:** the provider is part of the request (`POST /it-services/{id}/appointment-imports`, or the
  provider combo in the UI). It is never derived from the file name, which, as the samples show, can't be trusted.
- **Validation by the same rules as bookings:** each row is parsed independently and then scheduled through
  `AppointmentScheduler`, so it is checked against the same grid, duplicate and capacity rules as a booking, under the
  same provider lock (see 0003). Imported appointments therefore count toward availability immediately.
- **Partial import:** valid rows are imported and invalid rows are recorded. One bad line does not block hundreds of
  good ones, and the report says exactly what to fix.
- **Rejection reasons:** `MALFORMED_ROW`, `MISSING_FIELD`, `INVALID_CUSTOMER_NUMBER` (longer than 64 characters), `INVALID_DATE`, `OFF_GRID`, `OUTSIDE_OPENING_HOURS`, `UNKNOWN_SERVICE_CODE` (the
  message lists the codes the provider does offer), and `CAPACITY_EXCEEDED`. The legacy data could contain
  overbookings, and we do not take them over silently. Rows are processed in file order.
- **Idempotency:** a row with the same provider, service, customer and start is reported as a `DUPLICATE` and
  skipped. Re-running an import is safe.
- **Traceability:** an `import_batch` stores the file name, time and counters. `import_rejection` stores the line
  number (header = line 1), raw line, reason and message. Every imported appointment carries `source = IMPORTED` and
  its `import_batch_id`.
- **File-level errors:** an empty file, a missing or foreign header, duplicate column names or broken quoting return
  `400` and import nothing. The parser accepts a UTF-8 BOM, CRLF line endings, blank lines and surrounding whitespace.

## Consequences

- If the wrong provider is selected, the report lists the foreign service codes instead of importing them under the
  wrong provider. Shared codes such as DIA would still be imported with that provider's duration. This is unavoidable
  without a provider column in the file, and it is visible in the report.
- **Chunked processing:** an import runs in chunks of `appointments.import-chunk-size` rows (default 100). Each chunk
  is its own transaction under the provider lock, so a large file never blocks bookings of that provider for longer
  than one chunk.
  - Rows are still checked in file order with the same rules, and the results do not depend on chunk boundaries
    (`ChunkedImportTest`).
  - A booking that slips in between two chunks counts like any other existing appointment, so later rows may then be
    rejected for capacity. This matches the "first come, first served" rule of bookings.
  - **Import status:** every import record has a status: `RUNNING` while chunks are processed, then `COMPLETED`
    (every row processed) or `FAILED` (Flyway `V3`).
  - **On an unexpected error in a chunk** (e.g. a database error):
    - only that chunk is rolled back;
    - the record is marked `FAILED`, with a `failureMessage` naming the first line not processed and stating that
      re-importing is safe;
    - the client gets **500** with `importId` and a `Location` to the report;
    - internal details (SQL, stack traces) are logged on the server, not returned.
  - **On a lock timeout:** the record is also marked `FAILED`, and the client gets **503** + `Retry-After`, because
    that case is retryable.
  - **Why the status matters:** without it, an aborted import would look like a normal import with fewer rows,
    which is not "nachvollziehbar". Committed chunks stay imported, and re-running the file is safe, because
    already-imported rows are reported as duplicates.
  - **Limit:** if the database itself is unreachable, the `FAILED` mark cannot be written either, and the record
    stays `RUNNING`. That state is visible in the import list and the server log.
