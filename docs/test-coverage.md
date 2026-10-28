# Requirements → tests

Run everything with `./mvnw test` (150 tests). PostgreSQL runs via Testcontainers, and one test runs on H2.

There are two layers of tests:

1. **Acceptance suite** (`src/test/java/.../acceptance/`): 55 scenarios derived 1:1 from the task text. Every test
   method carries its scenario id (`C1_…`, `@DisplayName("C1 …")`), so the checklist below can be ticked off directly.
2. **Design tests** (all other test classes): the edge cases of the chosen design, e.g. opening hours, daylight
   saving time, lock timeout, H2 parity, the spec contract and CSV parser details. They are listed further down.

## 1. Acceptance suite

All scenarios except C1–C9 and C11–C13 go through the REST API against PostgreSQL. Those are domain-level unit tests of
`CapacityCalculator`. Times are UTC on Monday 2026-11-02; "now" is fixed to 2026-10-28T12:00Z.

| Area | Class | Scenarios |
|---|---|---|
| Capacity & overlap | `CapacityAndOverlapTest` (C1–C9, C11–C13), `BookingAcceptanceTest` (C10) | C1 empty schedule · C2 identical times · C3 touching after · C4 overlap at the end · C5 overlap at the start · C6 sequential appointments under a LAP · C7 three in the middle · C8 Schneider full · C9 Schneider 3rd yes / 4th no · C10 own capacity per provider · C11 new fully inside · C12 new fully encloses · C13 touching before |
| 15-minute grid | `GridAndEndTimeAcceptanceTest` | G1 :00/:15/:30/:45 accepted · G2 08:07 and 08:15:30 give 400 |
| End time | `GridAndEndTimeAcceptanceTest` | E1 Müller DIA/LAP/OSI (on three days, since three at 08:00 would exceed capacity 2) · E2 Schneider DIA 15 min |
| Suggestions | `SuggestionAcceptanceTest` | S1 every 15 min, end = start + 30, exactly the spec fields · S2 full · S3 one place left · S4 LAP around two DIA · S5 imported load · S6 Schneider capacity 3 · S7 every suggestion bookable · S8 400 · S9 404 |
| Booking | `BookingAcceptanceTest` | B1 201, spec body, persisted · B2 409 full · B3 409 filled after the suggestion · B4 409 filled by an import · B5 404 (incl. VIR at Müller) · B6 400 for each missing field |
| Concurrency | `ConcurrencyAcceptanceTest` | K1 20 parallel requests give 2 × 201 and 18 × 409 · K2 parallel LAP/DIA/OSI, peak in the DB ≤ 2 |
| CSV import | `ImportAcceptanceTest` | I1 task sample for Müller · I2 VIR unknown · I3 bad date / missing value / column count · I4 08:10Z · I5 3rd parallel row · I6 conflicts with existing bookings · I7 task sample for Schneider · I8 unknown provider, nothing imported · I9 `ABC-7` |
| Managing appointments | `AppointmentManagementAcceptanceTest` | L1 only own, booked + imported · L2 period boundaries · L3 service · L4 period + service · L5 customer · L6 details with all spec fields · L7 unknown / foreign gives 404 · L8 unknown provider gives 404 |
| Providers & services | `ProviderAcceptanceTest` | P1 list · P2 details / 404 · P3 services exactly as in the task tables |
| Extensibility | `ExtensibilityAcceptanceTest` | X1 a third provider from configuration (`application-x1.yml`): listing, suggestions, booking, capacity 1 |

### Traceability matrix

| Task requirement | Scenarios |
|---|---|
| Capacity never exceeded, at any point in time | C1–C13, K1, K2, I5, I6 |
| Overlaps (deliverable): all seven shapes – identical, end, start, inside, enclosing, touching after/before | C2–C8, C11–C13, S4 |
| 15-minute grid for bookings and imports | G1, G2, I4, S1 |
| Duration per provider, end-time calculation | E1, E2, S1, S6, I1, I7 |
| Req. 1: manage providers | P1, P2 |
| Req. 2: manage services | P3 |
| Req. 3: suggestions with all five factors | S1–S9 |
| Req. 4: booking with re-check | B1–B6, S7 |
| Req. 4: no overbooking under concurrency | K1, K2 |
| Req. 5: view, filter, details | L1–L8 |
| Req. 6: import format, provider association, error handling | I1–I9 |
| Imported appointments count toward availability | S5, B4, I6, L1 |
| Rules apply to imported appointments | I4, I5, I6 |
| REST API compliant with the spec (status codes, fields) | S8, S9, B1, B5, B6, L6–L8, P2; additionally `SpecContractTest` |
| Configurable and extensible | X1 |

### The suite detects real bugs

Each bug was planted in the production code, the suite was run, and the code was restored:

| Planted bug | Scenarios that fail |
|---|---|
| Count overlapping appointments instead of peak concurrency | C6 |
| Closed instead of half-open intervals (`<=` instead of `<`) | C3, S4, S5, X1 |
| Read the provider without `FOR UPDATE` | K1, K2 |
| Overlap = "new start or new end lies inside an existing appointment" (misses enclosing) | C12 |

### Decisions the scenarios depend on

**None of these is decided by the task.** Each quote below shows the room it leaves. The quotes are from
[`task/README.de.md`](task/README.de.md) and the original `rest-api-spec.yml`. The last paragraph of the task
explicitly allows documented deviations from the spec: *"Das bedeutete auch, das von der SPezifikation abgewichen
werden kann, sollte in diesen Fällen für die Nachvollziehbarkeit gestattet sein."*

| Decision | What the task / spec says | Our choice and why | Affects | ADR |
|---|---|---|---|---|
| Does `to` bound the start or the end of a suggestion? | Task, req. 3: *"Kunden sollen … verfügbare Termine erhalten."* Frontend: *"Eingabe eines gewünschten Zeitraums"*. Spec, `to`: *"Ende des Suchzeitraums (ISO 8601)"*. | **The end:** a suggestion must be finished by `to`. A customer who enters "08:00–12:00" means the appointment fits into that window, which is the natural reading of "end of the search period". S4 therefore uses `to = 13:00` instead of 12:00. | S1, S4 | [0004](adr/0004-time-model.md) |
| Partial import or all-or-nothing, and in which order? | Task, req. 6: *"Der Importprozess muss unbekannte Leistungscodes und ungültige Datensätze erkennen und nachvollziehbar behandeln."* and *"Die Regeln gelten sowohl für neu gebuchte als auch für importierte Termine."* No order is mentioned. | **Partial, in file order:** valid rows are imported, every other row is reported with line, raw content and reason. One bad line shouldn't block hundreds of good ones, and the report says exactly what to fix. | I3, I5 | [0005](adr/0005-csv-import-policy.md) |
| Import entry point | Task: *"Die Anwendung muss bestehende Termine aus CSV-Dateien importieren können."* and *"Die Zuordnung der CSV-Datei zum jeweiligen IT-Service-Anbieter muss beim Import eindeutig sein."* The spec has **no** import operation. | `POST /it-services/{serviceId}/appointment-imports` (multipart), a documented spec extension. The provider in the path makes the assignment unambiguous, and it is testable and usable from the frontend. | I8 | [0006](adr/0006-api-deviations.md) |
| Scope of `repairServiceId` | Task, req. 2: a service has *"Eindeutige Kennung"* and *"Leistungscode"*, and *"Auch bei identischen Leistungscodes können unterschiedliche Bearbeitungszeiten gelten."* Task: *"Jede Dienstleistung besitzt einen eindeutigen Leistungscode"*. Spec example: `id: DIA`, `code: DIA`. | **Unique per provider** (id = code), exactly as in the spec example. It is always resolved together with the provider in the path, so it identifies exactly one service wherever it is used. The full reasoning is in ADR 0002. | B5, S9 | [0002](adr/0002-configuration-driven-catalog.md) |
| Meaning of the inclusive period filter | Spec: `from` *"Beginn des Abfragezeitraums inklusive"*, `to` *"Ende des Abfragezeitraums inklusive"*. Task: *"Termine nach Zeitraum filtern können"*. | Every appointment running at some point of `[from, to]`: `end > from AND start ≤ to`. This is what staff mean by "appointments on Monday", including one that started before and is still running. | L2 | [0006](adr/0006-api-deviations.md) |

The scenario list classes opening hours, past-date rejection and re-import deduplication as "not required by the
task". This implementation has them anyway (documented deviations), and the scenario times were chosen so they don't
interfere: Monday, within opening hours, after "now".

## 2. Design tests

The following tables map further requirements and known pitfalls to the design tests. Test methods without a
class name are in the class of the row above or in the obvious class (`AppointmentApiTest`, `ImportApiTest`,
`ConcurrentBookingTest`).

### Functional requirements

| Requirement | Test |
|---|---|
| 1. Providers with id, name, capacity, services | `AppointmentApiTest.listsProvidersAndTheirServices`, `returnsASingleProvider` |
| 2. Services with id, code, name, duration; same code with a different duration per provider | `listsProvidersAndTheirServices`, `bookingComputesEndFromProviderSpecificDuration` |
| 3. Suggestions consider the duration, existing and imported appointments, capacity and the 15-minute grid | `suggestionsRespectCapacityOverTheWholeDuration`, `ImportApiTest.importedAppointmentsBlockSuggestionsAndBookings`, `SlotFinderTest` |
| 3. Only slots with capacity during the *entire* duration | `SlotFinderTest.slotIsRejectedWhenCapacityIsExceededLaterInTheDuration`, `CapacityCalculatorTest.overlapLaterInTheServiceDurationIsDetected` |
| 4. Booking re-checks availability | `bookingBeyondCapacityIsAConflict`, `importedAppointmentsBlockSuggestionsAndBookings` |
| 4. No overbooking under concurrent requests | `ConcurrentBookingTest` (all 5 tests) |
| 4. End time from the configured duration | `bookingComputesEndFromProviderSpecificDuration` |
| 5. All appointments of a provider, filtered by period and by service, with details | `listsAndFiltersAppointmentsOfOneProvider`, `returnsDetailsOnlyForTheOwningProvider` |
| 6. CSV import with an unambiguous provider | `ImportApiTest.importsLegacyFilesIntoTheProviderWhoseServicesTheyContain`, `importingIntoTheProviderNamedInTheFileNameRejectsForeignServiceCodes` |
| 6. Unknown codes and invalid records handled traceably | `reportsEveryInvalidRowWithLineNumberAndReason`, `CsvAppointmentParserTest` |
| 6. Imported appointments count for availability | `importedAppointmentsBlockSuggestionsAndBookings` |
| Rules also apply to imports (grid, capacity) | `reportsEveryInvalidRowWithLineNumberAndReason` (OFF_GRID, CAPACITY_EXCEEDED), `importRespectsAppointmentsBookedBefore` |
| Providers and services configurable, extensible without code changes | `CatalogExtensibilityTest` |
| REST API complies with `rest-api-spec.yml` | `SpecContractTest` (paths, fields); `SpecConformance` checks every status code and API header of every API test response |
| PostgreSQL **or** H2 | All integration tests on PostgreSQL; `H2ProfileTest` |
| Ext JS frontend served by the backend | `FrontendTest`. The UI flows were verified manually in headless Chrome (see [ai-usage.md](ai-usage.md)). |

### Pitfalls

| Pitfall | Test |
|---|---|
| Check-then-insert race | `parallelBookingsOfTheSameSlotNeverExceedCapacity`: 20 threads give exactly 2 created. Without the lock, 10 got through. |
| Overlapping parallel bookings with different starts and durations | `parallelOverlappingBookingsWithDifferentStartsNeverExceedCapacity` (peak load is checked in the DB) |
| Import racing with bookings, using the same lock path | `importRacingWithBookingsNeverExceedsCapacity`: fails when the import does not lock |
| The loser of a race gets HTTP 409, never two bookings | `AppointmentApiTest.parallelHttpRequestsGet201UntilCapacityThen409` |
| The lock is really taken (callers wait) and only per provider | `ConcurrentBookingTest.bookingAndImportWaitForTheProviderLockInsteadOfFailing`, `otherProvidersAreNotBlocked` |
| The lock behaves the same on H2 and PostgreSQL (H2 defaults to a ~2 s timeout) | `H2ProfileTest.bookingWaitsForTheProviderLockLikeOnPostgres`, `runsTheWholeSchedulingFlowOnH2` (without the lock: 3 at capacity 2) |
| Lock timeout gives 503 + Retry-After, not 500 | `LockTimeoutTest` |
| A large import does not block bookings of the same provider (chunked import) | `ChunkedImportTest.largeImportDoesNotBlockBookingsOfTheSameProvider`; fails when the whole file runs in one transaction |
| Import results are independent of chunk boundaries | `ChunkedImportTest.rulesHoldAcrossChunkBoundaries` |
| Opt-in paging with `X-Total-Count` (no parameters return everything), invalid page/size give 400 | `AppointmentApiTest.pagesThroughAppointmentsWithTotalCount` |
| An aborted import is recorded as FAILED with the line where it stopped, the client gets 500 + report link, re-import is safe | `ChunkedImportTest.unexpectedFailureMarksTheImportFailedAndReimportIsSafe`; on lock timeout 503 + FAILED: `LockTimeoutTest` |
| A too-long customer number in a CSV row rejects that row only | `ImportApiTest.rejectsATooLongCustomerNumberAsARowNotTheWholeFile` |
| Provider time zone and opening hours exposed for the UI | `AppointmentApiTest.exposesTimeZoneAndOpeningHoursOfEachProvider`, `SpecContractTest` |
| Duplicate submissions and double imports in parallel | `identicalParallelRequestsCreateExactlyOneAppointment`, `parallelImportsOfTheSameFileDoNotCreateDuplicates` |
| Peak concurrency instead of overlap count (A 08:00, B 08:30, C 09:00 under a LAP 08:00–10:00) | `AppointmentApiTest.peakConcurrencyNotOverlapCountDecides`, `CapacityCalculatorTest.sequentialShortAppointmentsCountAsOneInParallel` |
| Half-open intervals: back-to-back appointments are not an overlap | `CapacityCalculatorTest.touchingAppointmentsDoNotOverlap`, `bookingBeyondCapacityIsAConflict` (11:00 after 09:00–11:00) |
| Capacity 3 versus 2 | `schneiderAllowsThreeParallelAppointmentsButNotFour`, `SlotFinderTest.schneiderCapacityThreeAllowsAThirdParallelAppointment` |
| Off-grid start sent by a client (e.g. 09:10) | `rejectsStartOffTheQuarterHourGrid`, `TimeGridTest` |
| Unaligned search start and whether `to` bounds the end | `suggestionsRoundAnUnalignedFromUpAndEndByTo`, `SlotFinderTest.searchStartIsRoundedUpToTheGrid` |
| Invalid or too-large search range | `suggestionsValidateTheSearchRange` |
| Past slots | `rejectsBookingsInThePast`, `neverSuggestsPastSlots` |
| Opening hours per provider (deviation, ADR 0007) | `suggestionsFollowEachProvidersOpeningHours`, `rejectsBookingsOutsideOpeningHoursOrPastClosing`, `SlotFinderTest.onlySuggestsSlotsCompletelyWithinOpeningHours`, `OpeningHoursTest` |
| Time zone and daylight-saving switch (CET/CEST) | `OpeningHoursTest.followsTheDaylightSavingSwitchInAutumn` / `…InSpring`, `AppointmentApiTest.suggestionsFollowTheDaylightSavingSwitch` |
| Not past closing time, not across midnight | `OpeningHoursTest.appointmentMustLieCompletelyWithinTheDay`, `neverAcrossMidnight`, `rejectsBookingsOutsideOpeningHoursOrPastClosing` |
| Imports must respect opening hours too | `ImportApiTest.rejectsImportedAppointmentsOutsideOpeningHours` |
| Durations that are not multiples of 15 | `SlotFinderTest.durationsThatAreNotMultiplesOfFifteenMinutesWork`, `CatalogExtensibilityTest` (20 min) |
| A duration change must not move existing appointments | `CatalogExtensibilityTest` |
| DIA exists at both providers with different durations | `ImportApiTest.sameServiceCodeUsesTheDurationOfTheTargetProvider` |
| Order-dependent legacy conflicts: the first row in file order wins | `fileOrderDecidesWhichConflictingRowIsImported` |
| Re-import creates no duplicates | `reimportingTheSameFileOnlyReportsDuplicates` |
| Swapped CSV file names | `importingIntoTheProviderNamedInTheFileNameRejectsForeignServiceCodes` |
| CSV oddities: BOM, CRLF, blank lines, missing offset, extra columns, bad header | `CsvAppointmentParserTest`, `rejectsFilesThatCannotBeProcessedAtAll` |
| Time formats: no seconds, offsets, UTC output | `acceptsTimesWithoutSeconds`, `acceptsStartWithOffsetAndStoresUtc` |
| Another provider's appointment ID returns 404 | `returnsDetailsOnlyForTheOwningProvider` |
| Error bodies and status codes 400/404/409 | `errorsAreProblemDetails`, `rejectsBodiesWithMissingFields`, `unknownProviderOrServiceIsNotFound`, `sameCustomerServiceAndStartIsADuplicate` |
