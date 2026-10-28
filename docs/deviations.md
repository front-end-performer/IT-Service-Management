# Register der Abweichungen

Die Aufgabe erlaubt Abweichungen von der Spezifikation, sofern sie nachvollziehbar dokumentiert und begründet sind
(Aufgabentext: *"…das von der SPezifikation abgewichen werden kann, sollte in diesen Fällen für die
Nachvollziehbarkeit gestattet sein."*). Diese Seite listet **jede** Stelle, an der die Umsetzung von
[`task/README.de.md`](task/README.de.md) oder von der ursprünglichen `rest-api-spec.yml` (erster Commit) abweicht. Jeder
Eintrag nennt das Original, die Änderung, die Begründung, das ADR mit der ausführlichen Herleitung und den Test, der
das Verhalten festhält.

Die API-Änderungen wurden maschinell ermittelt: Die ursprüngliche und die aktuelle `rest-api-spec.yml` wurden geparst
und verglichen. Der Vergleich ergibt **ausschließlich Ergänzungen**. Kein Pfad, Parameter, Feld oder Statuscode der
Original-Spezifikation wurde entfernt oder in seiner Bedeutung geändert. In der YAML ist jede Ergänzung mit
„Erweiterung“ markiert. `SpecContractTest` (Pfade und Felder) und `SpecConformance` (jeder Statuscode und API-Header
jeder Antwort in den API-Tests) halten YAML und Code synchron.

Die Einträge fallen in drei Gruppen:
- **A – API-Erweiterungen** ergänzen etwas, das die Spezifikation nicht hat. Clients, die nur die Original-Spezifikation
  kennen, sind nicht betroffen.
- **B – Zusätzliche Statuscodes** decken Situationen ab, die die Spezifikation offenlässt.
- **C – Auslegungen** entscheiden, was Spezifikation oder Aufgabe offenlassen. Das sind keine Abweichungen im engeren
  Sinn, sie stehen hier aber, damit die Lesart nachvollziehbar ist.

**D – Abweichungen von der Aufgabe** stehen gesondert am Ende.

## A – API-Erweiterungen (ergänzend)

| # | Original | Änderung | Warum | ADR | Test |
|---|---|---|---|---|---|
| A1 | Keine Import-Operation, obwohl die Aufgabe einen CSV-Import verlangt | `POST /it-services/{serviceId}/appointment-imports` (Multipart), `GET …/appointment-imports[/{importId}]`; Definitionen `importReport`, `importRejection` | Die Aufgabe verlangt den Import und eine eindeutige Zuordnung zum Anbieter. Der Anbieter im Pfad liefert diese Zuordnung, und der Bericht macht die Fehlerbehandlung nachvollziehbar. | [0005](adr/0005-csv-import-policy.md), [0006](adr/0006-api-deviations.md) | `ImportAcceptanceTest` I1–I9, `ImportApiTest` |
| A2 | `appointment` hat keine Herkunft | `appointment.source` = `BOOKED` / `IMPORTED` | Mitarbeiter können Bestandstermine von neuen Buchungen unterscheiden (Nachvollziehbarkeit der Importe). | [0006](adr/0006-api-deviations.md) | L1, `ImportApiTest` |
| A3 | Statuscodes ohne Fehlerinhalt | RFC 9457 `application/problem+json` (Definition `problem`) bei jedem Fehler | Clients und Oberfläche brauchen den Grund, z. B. „No capacity left for LAP at …“. | [0006](adr/0006-api-deviations.md) | `AppointmentApiTest.errorsAreProblemDetails` |
| A4 | `201` ohne Header | Header `Location` bei angelegten Terminen und Importen | Übliche REST-Praxis. Der Inhalt ist unverändert. | [0006](adr/0006-api-deviations.md) | B1 |
| A5 | `GET …/appointments` liefert eine einfache Liste | **Optionales** Paging: `page`, `size` (1–500), Gesamtzahl in `X-Total-Count`. Ohne `page`/`size` kommen **alle** Termine, genau wie spezifiziert. | Die Liste wächst mit jeder Buchung und jedem Import, die Oberfläche muss also blättern können. Paging ist optional, damit ein Client, der der Original-Spezifikation folgt, nie unbemerkt eine gekürzte Liste erhält. | [0006](adr/0006-api-deviations.md) | `AppointmentApiTest.pagesThroughAppointmentsWithTotalCount` |
| A6 | `itService` = id, name, capacity | Zusätzlich `timeZone` und `openingHours` | Die Oberfläche zeigt und erwartet Zeiten in der Ortszeit des Anbieters und erklärt fehlende Termine. Nötig wegen D1. | [0006](adr/0006-api-deviations.md), [0007](adr/0007-opening-hours.md) | `AppointmentApiTest.exposesTimeZoneAndOpeningHoursOfEachProvider` |
| A7 | `appointmentRequest.customerNumber` hat keine Länge | `minLength: 1`, `maxLength: 64`, auch für CSV-Zeilen durchgesetzt (`INVALID_CUSTOMER_NUMBER`) | Entspricht der Speicherspalte. Ohne die Grenze ließ ein einziger zu langer CSV-Wert den ganzen Import mit 409 scheitern. Beim Erstellen dieses Registers gefunden, jetzt zeilenweise abgelehnt. | [0005](adr/0005-csv-import-policy.md) | `rejectsBodiesWithMissingFields`, `ImportApiTest.rejectsATooLongCustomerNumberAsARowNotTheWholeFile` |
| A8 | Es gibt keinen Import, also auch kein Importergebnis | `importReport.status` (`RUNNING`/`COMPLETED`/`FAILED`) und `failureMessage` | Ein abgebrochener Import muss als solcher erkennbar sein und sagen, wo er stehen blieb; ein erneuter Import ist sicher. | [0005](adr/0005-csv-import-policy.md) | `ChunkedImportTest.unexpectedFailureMarksTheImportFailedAndReimportIsSafe` |

## B – Zusätzliche Statuscodes

| # | Situation (in der Spezifikation undefiniert) | Antwort | Warum | ADR | Test |
|---|---|---|---|---|---|
| B1 | `GET …/appointments` mit nicht lesbarem Datum, `from > to` oder ungültigem `page`/`size` | `400` | Sonst undefiniert. Die Spezifikation hat für diese Operation kein `400`. | [0006](adr/0006-api-deviations.md) | `suggestionsValidateTheSearchRange`, `pagesThroughAppointmentsWithTotalCount` |
| B2 | Vorschlagszeitraum über 31 Tage | `400` (für einen ungültigen Zeitraum bereits spezifiziert) | Verhindert unbegrenzte Berechnungen. | [0004](adr/0004-time-model.md) | `suggestionsValidateTheSearchRange` |
| B3 | Buchungsstart in der Vergangenheit | `400` | Ein vergangener Termin kann nie verfügbar sein. Siehe D2. | [0004](adr/0004-time-model.md) | `rejectsBookingsInThePast` |
| B4 | Buchung außerhalb der Öffnungszeiten oder über die Schließzeit hinaus | `400` | Die Anfrage kann nie gelingen, also kein `409`. Siehe D1. | [0007](adr/0007-opening-hours.md) | `rejectsBookingsOutsideOpeningHoursOrPastClosing` |
| B5 | Gleicher Kunde, Dienst und Start zweimal gebucht | `409` | Eine Variante von „Termin nicht verfügbar“. Schützt vor Doppelklicks und Wiederholungen. | [0006](adr/0006-api-deviations.md) | `sameCustomerServiceAndStartIsADuplicate`, `identicalParallelRequestsCreateExactlyOneAppointment` |
| B6 | Anbietersperre nicht innerhalb von `appointments.lock-timeout-ms` (10 s) erlangt | `503` + `Retry-After: 1` | Ein Timeout sagt nichts über die Kapazität aus (also kein `409`) und ist wiederholbar (also kein `500`). | [0003](adr/0003-pessimistic-provider-lock.md) | `LockTimeoutTest` |
| B7 | CSV-Upload größer als 5 MB | `413` als Problem-Detail | Begrenzte Anfragegröße. Gegen die gepackte JAR geprüft (MockMvc setzt die Grenze nicht durch). | [0006](adr/0006-api-deviations.md) | Manuelle Prüfung, siehe [ai-usage.md](ai-usage.md) |
| B8 | Ein Import, der nach seiner Erfassung durch einen unerwarteten Fehler abbricht | `500` als Problem-Detail mit `importId` und `Location` zum `FAILED`-Bericht. Ein Sperr-Timeout während eines Imports ergibt `503` und wird ebenfalls als `FAILED` erfasst. | Der Client erfährt, dass der Import abgebrochen ist, wo, und dass ein erneuter Import sicher ist. Interne Details bleiben im Server-Log. | [0005](adr/0005-csv-import-policy.md) | `ChunkedImportTest`, `LockTimeoutTest.importThatTimesOutIsAnsweredWith503AndRecordedAsFailed` |

## C – Auslegungen offener Punkte

| # | Offener Punkt (Zitat) | Unsere Lesart | Warum | ADR | Test |
|---|---|---|---|---|---|
| C1 | Spezifikation `to`: *"Ende des Suchzeitraums"* | Ein Vorschlag muss bis `to` **enden** | Der Termin liegt innerhalb des Zeitraums, den der Kunde eingegeben hat. | [0004](adr/0004-time-model.md) | S1, S4 |
| C2 | Spezifikation `from`/`to`: *"… inklusive"* | Jeder Termin, der zu irgendeinem Zeitpunkt in `[from, to]` **läuft** | Das meinen Mitarbeiter mit „Termine am Montag“, einschließlich eines Termins, der bei `from` schon läuft. | [0006](adr/0006-api-deviations.md) | L2 |
| C3 | Aufgabe: *"Eindeutige Kennung"*. Beispiel der Spezifikation `id: DIA`, `code: DIA` | `repairServiceId` = Code, eindeutig **je Anbieter**, immer zusammen mit dem Anbieter im Pfad aufgelöst | Folgt dem Beispiel der Spezifikation. Derselbe Schlüssel wie in der CSV, stabil und lesbar. | [0002](adr/0002-configuration-driven-catalog.md) | B5, S9 |
| C4 | Aufgabe: *"…nachvollziehbar behandeln"* (kein Modus, keine Reihenfolge) | **Teilimport** in **Dateireihenfolge**, in Abschnitten zu 100 Zeilen | Eine fehlerhafte Zeile blockiert den Rest nicht, und große Dateien blockieren keine Buchungen. | [0005](adr/0005-csv-import-policy.md) | I3, I5, `ChunkedImportTest` |
| C5 | Aufgabe: Der Import muss die Datei *"eindeutig"* zuordnen | Der Anbieter kommt aus dem Anfragepfad, **nie** aus dem Dateinamen | Die Namen der Beispieldateien sind gegenüber ihrem Inhalt vertauscht. | [0005](adr/0005-csv-import-policy.md) | `importingIntoTheProviderNamedInTheFileNameRejectsForeignServiceCodes` |
| C6 | Beispiele der Spezifikation `09:00:00Z`, CSV `08:00Z` | Eingaben mit oder ohne Sekunden und mit beliebigem Offset; Ausgaben immer UTC `…Z` | Beide Formate kommen im Originalmaterial vor. | [0006](adr/0006-api-deviations.md) | `acceptsTimesWithoutSeconds`, `acceptsStartWithOffsetAndStoresUtc` |

## D – Abweichungen von der Aufgabe

| # | Aufgabe sagt | Änderung | Warum | ADR | Test |
|---|---|---|---|---|---|
| D1 | Keine Öffnungszeiten definiert | Öffnungszeiten **je Anbieter**, in Ortszeit (`Europe/Berlin`) mit Sommerzeit. Ein Termin darf nicht über die Schließzeit oder über Mitternacht laufen. Gilt für Vorschläge, Buchungen und Importe. | Ohne sie werden Termine um 03:00 oder an Sonntagen angeboten. Die konfigurierten Zeiten umfassen alle 40 Bestandstermine. | [0007](adr/0007-opening-hours.md) | `OpeningHoursTest`, `suggestionsFollowEachProvidersOpeningHours`, `rejectsImportedAppointmentsOutsideOpeningHours` |
| D2 | Nichts zu vergangenen Zeitpunkten | Keine Vorschläge oder Buchungen in der Vergangenheit. Importe dürfen historisch sein. | Ein vergangener Termin kann nie angeboten werden. Bestandstermine sind Historie. | [0004](adr/0004-time-model.md) | `rejectsBookingsInThePast`, `neverSuggestsPastSlots` |
| D3 | `docker-compose.yml` mit `postgres:11-alpine`, Port `5432` | `postgres:17-alpine`, Port nur an `127.0.0.1` gebunden, Port über `DB_PORT` änderbar (Standard `5432`, wird auch von der Anwendung gelesen). Veralteter Schlüssel `version` entfernt. Datenbankname, Benutzer und Passwort unverändert. | PostgreSQL 11 wird seit November 2023 nicht mehr unterstützt. Die Datenbank soll nicht aus dem Netz erreichbar sein. Ein lokal installiertes PostgreSQL belegt oft 5432; ohne `DB_PORT` verbindet sich die Anwendung dann unbemerkt damit und scheitert mit einem irreführenden Authentifizierungsfehler. | [0001](adr/0001-technology-stack.md) | Alle Integrationstests laufen auf PostgreSQL 17 |

Sonst unterscheidet sich nichts: Alle Pfade, Parameter, Feldnamen, Pflichtfelder und Statuscodes der
Original-Spezifikation verhalten sich wie spezifiziert. Das sichern `SpecContractTest`, `SpecConformance` und die
Abnahme-Suite.
