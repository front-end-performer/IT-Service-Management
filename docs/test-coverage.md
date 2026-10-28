# Requirements → tests

Each requirement of the [task](task/README.de.md) and each known pitfall, with the test that proves it. Run everything
with `./mvnw test` (83 tests). PostgreSQL runs via Testcontainers, and one test runs on H2.

Unless a different class is named, test methods without a class are in the class of the row above or in the obvious
class (`AppointmentApiTest`, `ImportApiTest`, `ConcurrentBookingTest`).

## Functional requirements

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
| REST API complies with `rest-api-spec.yml` | `SpecContractTest` |
| PostgreSQL **or** H2 | All integration tests on PostgreSQL; `H2ProfileTest` |
| Ext JS frontend served by the backend | `FrontendTest`. The UI flows were verified manually in headless Chrome (see [ai-usage.md](ai-usage.md)). |

## Pitfalls

| Pitfall | Test |
|---|---|
| Check-then-insert race | `parallelBookingsOfTheSameSlotNeverExceedCapacity`: 20 threads give exactly 2 created. Without the lock, 10 got through. |
| Overlapping parallel bookings with different starts and durations | `parallelOverlappingBookingsWithDifferentStartsNeverExceedCapacity` (peak load is checked in the DB) |
| Import racing with bookings, using the same lock path | `importRacingWithBookingsNeverExceedsCapacity`: fails when the import does not lock |
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
