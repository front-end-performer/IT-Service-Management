# Anforderungen → Tests

Alles ausführen mit `./mvnw test` (150 Tests). PostgreSQL läuft über Testcontainers, ein Test läuft auf H2.

Es gibt zwei Testebenen:

1. **Abnahme-Suite** (`src/test/java/.../acceptance/`): 55 Szenarien, 1:1 aus der Aufgabenstellung abgeleitet. Jede
   Testmethode trägt ihre Szenario-ID (`C1_…`, `@DisplayName("C1 …")`), sodass sich die Checkliste unten direkt
   abhaken lässt.
2. **Entwurfstests** (alle übrigen Testklassen): die Grenzfälle des gewählten Entwurfs, z. B. Öffnungszeiten,
   Sommerzeit, Sperr-Timeout, Gleichverhalten von H2, Vertrag mit der Spezifikation und Details des CSV-Parsers. Sie
   stehen weiter unten.

## 1. Abnahme-Suite

Alle Szenarien außer C1–C9 und C11–C13 laufen über die REST-API gegen PostgreSQL. Diese sind Unit-Tests von
`CapacityCalculator` auf Domänenebene. Die Zeiten sind UTC am Montag, 2026-11-02; „jetzt“ ist fest auf
2026-10-28T12:00Z gesetzt.

| Bereich | Klasse | Szenarien |
|---|---|---|
| Kapazität & Überschneidung | `CapacityAndOverlapTest` (C1–C9, C11–C13), `BookingAcceptanceTest` (C10) | C1 leerer Plan · C2 identische Zeiten · C3 grenzt danach an · C4 Überschneidung am Ende · C5 Überschneidung am Anfang · C6 aufeinanderfolgende Termine unter einem LAP · C7 drei in der Mitte · C8 Schneider voll · C9 Schneider 3. ja / 4. nein · C10 eigene Kapazität je Anbieter · C11 neuer Termin vollständig innerhalb · C12 neuer Termin umschließt vollständig · C13 grenzt davor an |
| 15-Minuten-Raster | `GridAndEndTimeAcceptanceTest` | G1 :00/:15/:30/:45 angenommen · G2 08:07 und 08:15:30 ergeben 400 |
| Endzeit | `GridAndEndTimeAcceptanceTest` | E1 Müller DIA/LAP/OSI (an drei Tagen, weil drei um 08:00 die Kapazität 2 überschreiten würden) · E2 Schneider DIA 15 min |
| Vorschläge | `SuggestionAcceptanceTest` | S1 alle 15 min, Ende = Start + 30, genau die Felder der Spezifikation · S2 voll · S3 ein Platz frei · S4 LAP um zwei DIA herum · S5 importierte Last · S6 Schneider Kapazität 3 · S7 jeder Vorschlag buchbar · S8 400 · S9 404 |
| Buchung | `BookingAcceptanceTest` | B1 201, Inhalt laut Spezifikation, gespeichert · B2 409 voll · B3 409 nach dem Vorschlag belegt · B4 409 durch einen Import belegt · B5 404 (inkl. VIR bei Müller) · B6 400 für jedes fehlende Feld |
| Nebenläufigkeit | `ConcurrencyAcceptanceTest` | K1 20 parallele Anfragen ergeben 2 × 201 und 18 × 409 · K2 parallele LAP/DIA/OSI, Spitze in der DB ≤ 2 |
| CSV-Import | `ImportAcceptanceTest` | I1 Beispiel aus der Aufgabe für Müller · I2 VIR unbekannt · I3 falsches Datum / fehlender Wert / Spaltenzahl · I4 08:10Z · I5 3. parallele Zeile · I6 Konflikt mit bestehenden Buchungen · I7 Beispiel aus der Aufgabe für Schneider · I8 unbekannter Anbieter, nichts importiert · I9 `ABC-7` |
| Termine verwalten | `AppointmentManagementAcceptanceTest` | L1 nur eigene, gebucht + importiert · L2 Zeitraumgrenzen · L3 Dienstleistung · L4 Zeitraum + Dienstleistung · L5 Kunde · L6 Details mit allen Feldern der Spezifikation · L7 unbekannt / fremd ergibt 404 · L8 unbekannter Anbieter ergibt 404 |
| Anbieter & Dienstleistungen | `ProviderAcceptanceTest` | P1 Liste · P2 Details / 404 · P3 Dienstleistungen genau wie in den Tabellen der Aufgabe |
| Erweiterbarkeit | `ExtensibilityAcceptanceTest` | X1 ein dritter Anbieter aus der Konfiguration (`application-x1.yml`): Auflistung, Vorschläge, Buchung, Kapazität 1 |

### Rückverfolgbarkeitsmatrix

| Anforderung der Aufgabe | Szenarien |
|---|---|
| Kapazität wird zu keinem Zeitpunkt überschritten | C1–C13, K1, K2, I5, I6 |
| Terminüberschneidungen (Lieferumfang): alle sieben Formen – identisch, Ende, Anfang, innerhalb, umschließend, grenzt danach/davor an | C2–C8, C11–C13, S4 |
| 15-Minuten-Raster für Buchungen und Importe | G1, G2, I4, S1 |
| Dauer je Anbieter, Berechnung der Endzeit | E1, E2, S1, S6, I1, I7 |
| Anf. 1: Anbieter verwalten | P1, P2 |
| Anf. 2: Dienstleistungen verwalten | P3 |
| Anf. 3: Vorschläge mit allen fünf Faktoren | S1–S9 |
| Anf. 4: Buchung mit erneuter Prüfung | B1–B6, S7 |
| Anf. 4: keine Überbuchung bei Nebenläufigkeit | K1, K2 |
| Anf. 5: einsehen, filtern, Details | L1–L8 |
| Anf. 6: Importformat, Zuordnung zum Anbieter, Fehlerbehandlung | I1–I9 |
| Importierte Termine zählen für die Verfügbarkeit | S5, B4, I6, L1 |
| Regeln gelten auch für importierte Termine | I4, I5, I6 |
| REST-API gemäß Spezifikation (Statuscodes, Felder) | S8, S9, B1, B5, B6, L6–L8, P2; zusätzlich `SpecContractTest` |
| Konfigurierbar und erweiterbar | X1 |

### Die Suite erkennt echte Fehler

Jeder Fehler wurde in den Produktivcode eingebaut, die Suite ausgeführt und der Code wiederhergestellt:

| Eingebauter Fehler | Fehlschlagende Szenarien |
|---|---|
| Überlappende Termine zählen statt Spitzenlast | C6 |
| Geschlossene statt halboffener Intervalle (`<=` statt `<`) | C3, S4, S5, X1 |
| Anbieter ohne `FOR UPDATE` lesen | K1, K2 |
| Überschneidung = „neuer Start oder neues Ende liegt in einem bestehenden Termin“ (übersieht das Umschließen) | C12 |

### Entscheidungen, von denen die Szenarien abhängen

**Keine davon legt die Aufgabe fest.** Jedes Zitat unten zeigt den Spielraum, den sie lässt. Die Zitate stammen aus
[`task/README.de.md`](task/README.de.md) und der ursprünglichen `rest-api-spec.yml`. Der letzte Absatz der Aufgabe
erlaubt ausdrücklich dokumentierte Abweichungen von der Spezifikation: *"Das bedeutete auch, das von der
SPezifikation abgewichen werden kann, sollte in diesen Fällen für die Nachvollziehbarkeit gestattet sein."*

| Entscheidung | Was Aufgabe / Spezifikation sagen | Unsere Wahl und warum | Betrifft | ADR |
|---|---|---|---|---|
| Begrenzt `to` den Start oder das Ende eines Vorschlags? | Aufgabe, Anf. 3: *"Kunden sollen … verfügbare Termine erhalten."* Frontend: *"Eingabe eines gewünschten Zeitraums"*. Spezifikation, `to`: *"Ende des Suchzeitraums (ISO 8601)"*. | **Das Ende:** Ein Vorschlag muss bis `to` beendet sein. Wer „08:00–12:00“ eingibt, meint, dass der Termin in dieses Fenster passt; das ist die naheliegende Lesart von „Ende des Suchzeitraums“. S4 verwendet deshalb `to = 13:00` statt 12:00. | S1, S4 | [0004](adr/0004-time-model.md) |
| Teilimport oder alles oder nichts, und in welcher Reihenfolge? | Aufgabe, Anf. 6: *"Der Importprozess muss unbekannte Leistungscodes und ungültige Datensätze erkennen und nachvollziehbar behandeln."* und *"Die Regeln gelten sowohl für neu gebuchte als auch für importierte Termine."* Eine Reihenfolge wird nicht genannt. | **Teilimport in Dateireihenfolge:** Gültige Zeilen werden importiert, jede andere wird mit Zeile, Rohinhalt und Grund gemeldet. Eine fehlerhafte Zeile soll nicht hundert gültige blockieren, und der Bericht sagt genau, was zu korrigieren ist. | I3, I5 | [0005](adr/0005-csv-import-policy.md) |
| Einstiegspunkt des Imports | Aufgabe: *"Die Anwendung muss bestehende Termine aus CSV-Dateien importieren können."* und *"Die Zuordnung der CSV-Datei zum jeweiligen IT-Service-Anbieter muss beim Import eindeutig sein."* Die Spezifikation hat **keine** Import-Operation. | `POST /it-services/{serviceId}/appointment-imports` (Multipart), eine dokumentierte Erweiterung der Spezifikation. Der Anbieter im Pfad macht die Zuordnung eindeutig, und der Endpunkt ist testbar und aus dem Frontend nutzbar. | I8 | [0006](adr/0006-api-deviations.md) |
| Geltungsbereich von `repairServiceId` | Aufgabe, Anf. 2: Eine Dienstleistung hat *"Eindeutige Kennung"* und *"Leistungscode"*, und *"Auch bei identischen Leistungscodes können unterschiedliche Bearbeitungszeiten gelten."* Aufgabe: *"Jede Dienstleistung besitzt einen eindeutigen Leistungscode"*. Beispiel der Spezifikation: `id: DIA`, `code: DIA`. | **Eindeutig je Anbieter** (ID = Code), genau wie im Beispiel der Spezifikation. Sie wird immer zusammen mit dem Anbieter im Pfad aufgelöst und bezeichnet daher überall, wo sie verwendet wird, genau eine Dienstleistung. Die ausführliche Begründung steht in ADR 0002. | B5, S9 | [0002](adr/0002-configuration-driven-catalog.md) |
| Bedeutung des inklusiven Zeitraumfilters | Spezifikation: `from` *"Beginn des Abfragezeitraums inklusive"*, `to` *"Ende des Abfragezeitraums inklusive"*. Aufgabe: *"Termine nach Zeitraum filtern können"*. | Jeder Termin, der zu irgendeinem Zeitpunkt in `[from, to]` läuft: `end > from AND start ≤ to`. Das meinen Mitarbeiter mit „Termine am Montag“, einschließlich eines Termins, der vorher begann und noch läuft. | L2 | [0006](adr/0006-api-deviations.md) |

Die Szenarioliste stuft Öffnungszeiten, die Ablehnung vergangener Zeitpunkte und die Duplikaterkennung beim Neu-Import
als „von der Aufgabe nicht verlangt“ ein. Diese Umsetzung hat sie trotzdem (dokumentierte Abweichungen), und die Zeiten
der Szenarien wurden so gewählt, dass sie nicht stören: Montag, innerhalb der Öffnungszeiten, nach „jetzt“.

## 2. Entwurfstests

Die folgenden Tabellen ordnen weitere Anforderungen und bekannte Fallstricke den Entwurfstests zu. Testmethoden ohne
Klassennamen stehen in der Klasse der Zeile darüber oder in der naheliegenden Klasse (`AppointmentApiTest`,
`ImportApiTest`, `ConcurrentBookingTest`).

### Funktionale Anforderungen

| Anforderung | Test |
|---|---|
| 1. Anbieter mit Kennung, Name, Kapazität, Dienstleistungen | `AppointmentApiTest.listsProvidersAndTheirServices`, `returnsASingleProvider` |
| 2. Dienstleistungen mit Kennung, Code, Bezeichnung, Dauer; gleicher Code mit anderer Dauer je Anbieter | `listsProvidersAndTheirServices`, `bookingComputesEndFromProviderSpecificDuration` |
| 3. Vorschläge berücksichtigen Dauer, bestehende und importierte Termine, Kapazität und das 15-Minuten-Raster | `suggestionsRespectCapacityOverTheWholeDuration`, `ImportApiTest.importedAppointmentsBlockSuggestionsAndBookings`, `SlotFinderTest` |
| 3. Nur Termine mit Kapazität während der *gesamten* Dauer | `SlotFinderTest.slotIsRejectedWhenCapacityIsExceededLaterInTheDuration`, `CapacityCalculatorTest.overlapLaterInTheServiceDurationIsDetected` |
| 4. Buchung prüft die Verfügbarkeit erneut | `bookingBeyondCapacityIsAConflict`, `importedAppointmentsBlockSuggestionsAndBookings` |
| 4. Keine Überbuchung bei gleichzeitigen Anfragen | `ConcurrentBookingTest` (alle Tests) |
| 4. Endzeit aus der konfigurierten Dauer | `bookingComputesEndFromProviderSpecificDuration` |
| 5. Alle Termine eines Anbieters, nach Zeitraum und Dienstleistung gefiltert, mit Details | `listsAndFiltersAppointmentsOfOneProvider`, `returnsDetailsOnlyForTheOwningProvider` |
| 6. CSV-Import mit eindeutigem Anbieter | `ImportApiTest.importsLegacyFilesIntoTheProviderWhoseServicesTheyContain`, `importingIntoTheProviderNamedInTheFileNameRejectsForeignServiceCodes` |
| 6. Unbekannte Codes und ungültige Datensätze nachvollziehbar behandelt | `reportsEveryInvalidRowWithLineNumberAndReason`, `CsvAppointmentParserTest` |
| 6. Importierte Termine zählen für die Verfügbarkeit | `importedAppointmentsBlockSuggestionsAndBookings` |
| Regeln gelten auch für Importe (Raster, Kapazität) | `reportsEveryInvalidRowWithLineNumberAndReason` (OFF_GRID, CAPACITY_EXCEEDED), `importRespectsAppointmentsBookedBefore` |
| Anbieter und Dienstleistungen konfigurierbar, erweiterbar ohne Codeänderungen | `CatalogExtensibilityTest` |
| REST-API gemäß `rest-api-spec.yml` | `SpecContractTest` (Pfade, Felder); `SpecConformance` prüft jeden Statuscode und API-Header jeder Antwort in den API-Tests |
| PostgreSQL **oder** H2 | Alle Integrationstests auf PostgreSQL; `H2ProfileTest` |
| Ext-JS-Frontend vom Backend ausgeliefert | `FrontendTest`. Die Abläufe der Oberfläche wurden manuell per Headless-Chrome geprüft (siehe [ai-usage.md](ai-usage.md)). |

### Fallstricke

| Fallstrick | Test |
|---|---|
| Wettlauf zwischen Prüfen und Einfügen | `parallelBookingsOfTheSameSlotNeverExceedCapacity`: 20 Threads ergeben genau 2 Buchungen. Ohne Sperre gingen 10 durch. |
| Überlappende parallele Buchungen mit unterschiedlichen Starts und Dauern | `parallelOverlappingBookingsWithDifferentStartsNeverExceedCapacity` (Spitzenlast wird in der DB geprüft) |
| Import im Wettlauf mit Buchungen, über denselben Sperrpfad | `importRacingWithBookingsNeverExceedsCapacity`: schlägt fehl, wenn der Import nicht sperrt |
| Der Verlierer eines Wettlaufs erhält HTTP 409, nie zwei Buchungen | `AppointmentApiTest.parallelHttpRequestsGet201UntilCapacityThen409` |
| Die Sperre wird tatsächlich genommen (Aufrufer warten) und nur je Anbieter | `ConcurrentBookingTest.bookingAndImportWaitForTheProviderLockInsteadOfFailing`, `otherProvidersAreNotBlocked` |
| Die Sperre verhält sich auf H2 und PostgreSQL gleich (H2 hat standardmäßig ~2 s Timeout) | `H2ProfileTest.bookingWaitsForTheProviderLockLikeOnPostgres`, `runsTheWholeSchedulingFlowOnH2` (ohne Sperre: 3 bei Kapazität 2) |
| Sperr-Timeout ergibt 503 + Retry-After, nicht 500 | `LockTimeoutTest` |
| Ein großer Import blockiert keine Buchungen desselben Anbieters (Import in Abschnitten) | `ChunkedImportTest.largeImportDoesNotBlockBookingsOfTheSameProvider`; schlägt fehl, wenn die ganze Datei in einer Transaktion läuft |
| Importergebnisse hängen nicht von Abschnittsgrenzen ab | `ChunkedImportTest.rulesHoldAcrossChunkBoundaries` |
| Optionales Paging mit `X-Total-Count` (ohne Parameter kommt alles), ungültiges page/size ergibt 400 | `AppointmentApiTest.pagesThroughAppointmentsWithTotalCount` |
| Ein abgebrochener Import wird als FAILED mit der Abbruchzeile erfasst, der Client erhält 500 + Link zum Bericht, ein erneuter Import ist sicher | `ChunkedImportTest.unexpectedFailureMarksTheImportFailedAndReimportIsSafe`; bei Sperr-Timeout 503 + FAILED: `LockTimeoutTest` |
| Eine zu lange Kundennummer in einer CSV-Zeile lehnt nur diese Zeile ab | `ImportApiTest.rejectsATooLongCustomerNumberAsARowNotTheWholeFile` |
| Zeitzone und Öffnungszeiten des Anbieters für die Oberfläche verfügbar | `AppointmentApiTest.exposesTimeZoneAndOpeningHoursOfEachProvider`, `SpecContractTest` |
| Doppelt abgeschickte Buchungen und doppelte Importe parallel | `identicalParallelRequestsCreateExactlyOneAppointment`, `parallelImportsOfTheSameFileDoNotCreateDuplicates` |
| Spitzenlast statt Anzahl der Überschneidungen (A 08:00, B 08:30, C 09:00 unter einem LAP 08:00–10:00) | `AppointmentApiTest.peakConcurrencyNotOverlapCountDecides`, `CapacityCalculatorTest.sequentialShortAppointmentsCountAsOneInParallel` |
| Halboffene Intervalle: unmittelbar aufeinanderfolgende Termine überschneiden sich nicht | `CapacityCalculatorTest.touchingAppointmentsDoNotOverlap`, `bookingBeyondCapacityIsAConflict` (11:00 nach 09:00–11:00) |
| Kapazität 3 gegenüber 2 | `schneiderAllowsThreeParallelAppointmentsButNotFour`, `SlotFinderTest.schneiderCapacityThreeAllowsAThirdParallelAppointment` |
| Start außerhalb des Rasters, von einem Client geschickt (z. B. 09:10) | `rejectsStartOffTheQuarterHourGrid`, `TimeGridTest` |
| Nicht ausgerichteter Suchbeginn, und ob `to` das Ende begrenzt | `suggestionsRoundAnUnalignedFromUpAndEndByTo`, `SlotFinderTest.searchStartIsRoundedUpToTheGrid` |
| Ungültiger oder zu großer Suchzeitraum | `suggestionsValidateTheSearchRange` |
| Vergangene Zeitpunkte | `rejectsBookingsInThePast`, `neverSuggestsPastSlots` |
| Öffnungszeiten je Anbieter (Abweichung, ADR 0007) | `suggestionsFollowEachProvidersOpeningHours`, `rejectsBookingsOutsideOpeningHoursOrPastClosing`, `SlotFinderTest.onlySuggestsSlotsCompletelyWithinOpeningHours`, `OpeningHoursTest` |
| Zeitzone und Zeitumstellung (MEZ/MESZ) | `OpeningHoursTest.followsTheDaylightSavingSwitchInAutumn` / `…InSpring`, `AppointmentApiTest.suggestionsFollowTheDaylightSavingSwitch` |
| Nicht über die Schließzeit, nicht über Mitternacht | `OpeningHoursTest.appointmentMustLieCompletelyWithinTheDay`, `neverAcrossMidnight`, `rejectsBookingsOutsideOpeningHoursOrPastClosing` |
| Auch Importe müssen die Öffnungszeiten einhalten | `ImportApiTest.rejectsImportedAppointmentsOutsideOpeningHours` |
| Dauern, die keine Vielfachen von 15 sind | `SlotFinderTest.durationsThatAreNotMultiplesOfFifteenMinutesWork`, `CatalogExtensibilityTest` (20 min) |
| Eine geänderte Dauer darf bestehende Termine nicht verschieben | `CatalogExtensibilityTest` |
| DIA gibt es bei beiden Anbietern mit unterschiedlicher Dauer | `ImportApiTest.sameServiceCodeUsesTheDurationOfTheTargetProvider` |
| Reihenfolgeabhängige Konflikte in Bestandsdaten: Die erste Zeile in Dateireihenfolge gewinnt | `fileOrderDecidesWhichConflictingRowIsImported` |
| Ein erneuter Import erzeugt keine Duplikate | `reimportingTheSameFileOnlyReportsDuplicates` |
| Vertauschte Namen der CSV-Dateien | `importingIntoTheProviderNamedInTheFileNameRejectsForeignServiceCodes` |
| CSV-Besonderheiten: BOM, CRLF, Leerzeilen, fehlender Offset, zusätzliche Spalten, falscher Header | `CsvAppointmentParserTest`, `rejectsFilesThatCannotBeProcessedAtAll` |
| Zeitformate: ohne Sekunden, Offsets, Ausgabe in UTC | `acceptsTimesWithoutSeconds`, `acceptsStartWithOffsetAndStoresUtc` |
| Die Termin-ID eines anderen Anbieters ergibt 404 | `returnsDetailsOnlyForTheOwningProvider` |
| Fehlerinhalte und Statuscodes 400/404/409 | `errorsAreProblemDetails`, `rejectsBodiesWithMissingFields`, `unknownProviderOrServiceIsNotFound`, `sameCustomerServiceAndStartIsADuplicate` |
