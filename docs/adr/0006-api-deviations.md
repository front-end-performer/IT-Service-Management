# 0006 – Abweichungen von und Erweiterungen zu `rest-api-spec.yml`

**Status:** angenommen

Die Spezifikation ist wie geschrieben umgesetzt: alle Pfade, Parameter, Statuscodes und Feldnamen. Alles unten
Aufgeführte ist eine Ergänzung; es steht in [`rest-api-spec.yml`](../../rest-api-spec.yml) und ist dort mit
„Erweiterung“ markiert.

Das vollständige Register der Abweichungen, einschließlich der Abweichungen von der Aufgabe, der Auslegungen und des
jeweiligen Tests, steht in [`docs/deviations.md`](../deviations.md). Dieses ADR enthält die Begründungen zur API.

| Änderung | Begründung |
|---|---|
| `POST/GET /it-services/{serviceId}/appointment-imports[/{importId}]` (Multipart-Upload, Importbericht) | Die Aufgabe verlangt *"Die Anwendung muss bestehende Termine aus CSV-Dateien importieren können"*, und die Spezifikation hat dafür keinen Endpunkt. Die Aufgabe erlaubt Abweichungen: *"…das von der SPezifikation abgewichen werden kann, sollte in diesen Fällen für die Nachvollziehbarkeit gestattet sein."* Der Anbieter im Pfad erfüllt *"Die Zuordnung der CSV-Datei zum jeweiligen IT-Service-Anbieter muss beim Import eindeutig sein."* |
| `appointment.source` (`BOOKED` / `IMPORTED`) | Mitarbeiter können Bestandstermine von neuen Buchungen unterscheiden. Ein ergänzendes, abwärtskompatibles Feld. |
| Fehlerinhalte als RFC 9457 `application/problem+json` (Definition `problem`) | Die Spezifikation definiert Statuscodes, aber keinen Fehlerinhalt. Clients und Oberfläche brauchen den Grund, z. B. „No capacity left for LAP at …“. |
| `400` bei `GET …/appointments` für ungültiges `from`/`to` | Nicht lesbare Daten und `from > to` waren sonst undefiniert. |
| `400` bei Vorschlägen für einen Zeitraum über 31 Tage | Verhindert unbegrenzte Berechnungen. Der Statuscode war bereits spezifiziert. |
| `400` für eine Buchung außerhalb der Öffnungszeiten des Anbieters (oder über die Schließzeit hinaus); keine Vorschläge außerhalb | Öffnungszeiten sind eine dokumentierte Abweichung, siehe [ADR 0007](0007-opening-hours.md). Die Anfrage kann nie gelingen, deshalb 400 statt 409. |
| `400` für einen Buchungsstart in der Vergangenheit | Nicht spezifiziert. Ein vergangener Termin kann nie „verfügbar“ sein. |
| `409` auch für eine doppelte Buchung (gleicher Kunde, Dienst und Start) | Die Spezifikation beschreibt 409 als „Termin nicht verfügbar“. Ein Duplikat ist eine Variante davon. |
| `503` + `Retry-After` bei Buchung/Import, wenn die Anbietersperre nicht innerhalb von `appointments.lock-timeout-ms` erlangt wird | Nicht spezifiziert. Ein Timeout sagt nichts über die Verfügbarkeit aus, also kein 409. Anders als ein 500 ist er wiederholbar. Siehe ADR 0003. |
| Header `Location` bei `201` | Übliche REST-Praxis. Der Inhalt ist unverändert. |
| **Optionales** `page`/`size` bei `GET …/appointments` (size 1–500; nur `page` angegeben bedeutet size 100), Gesamtzahl in `X-Total-Count` | Die Liste wächst mit jeder Buchung und jedem Import, die Oberfläche muss also blättern können. Ohne `page`/`size` kommen **alle** Termine, genau wie spezifiziert; ein Client, der nur die Original-Spezifikation kennt, erhält also nie unbemerkt eine gekürzte Liste. (Eine erste Version hat standardmäßig geblättert; das wurde aus diesem Grund geändert.) |
| `customerNumber` auf 1–64 Zeichen begrenzt (Buchungen: 400; CSV-Zeilen: `INVALID_CUSTOMER_NUMBER`) | Entspricht der Speicherspalte. Vor der CSV-Prüfung ließ ein einziger zu langer Wert einen ganzen Import mit 409 scheitern. |
| `413` als Problem-Detail für einen CSV-Upload über 5 MB | Begrenzte Anfragegröße. Gegen die gepackte JAR geprüft. |
| `importReport.status` / `failureMessage`; `500` (+ `importId`, `Location`), wenn ein Import abbricht, und `503` vom Import bei einem Sperr-Timeout | Ein abgebrochener Import muss als abgebrochen erkennbar sein und sagen, wo er stehen blieb und dass ein erneuter Import sicher ist (Aufgabe: „nachvollziehbar behandeln“). |
| `itService.timeZone` und `itService.openingHours` | Clients brauchen die Zeitzone des Anbieters, um Ortszeiten anzuzeigen und anzunehmen, und die Öffnungszeiten, um fehlende Termine zu erklären. Ergänzende Felder. |

**Benennung:** Die Spezifikation nennt den Anbieter `serviceId` und dessen Dienstleistung `repairServiceId`. Das bleibt
aus Kompatibilitätsgründen so. Intern verwendet der Code `Provider` und `ServiceOffering`, und die DTO-Abbildung ist
der einzige Ort, an dem sich beide Begriffswelten treffen (`api/Dtos.java`).

**Zeitformate:** Eingaben akzeptieren Sekunden oder keine Sekunden und jeden Offset (`2026-11-02T09:00:00Z`,
`2026-11-02T08:00Z`, `2026-11-02T10:00+01:00`). Ausgaben sind immer UTC mit `Z`.

**Vertragsprüfung:** Die YAML-Datei ist maßgeblich, und Code wird nicht aus ihr generiert. Zwei Prüfungen halten Code und
YAML zusammen:
- **`SpecContractTest`** (statisch) lässt den Build scheitern, wenn ein implementierter Endpunkt in der Spezifikation
  fehlt oder umgekehrt, oder wenn die JSON-Felder eines DTOs von den Eigenschaften der passenden Definition abweichen.
- **`SpecConformance`** (zur Laufzeit) prüft *jede* MockMvc-Antwort *jedes* Integrationstests:
  - die Operation muss dokumentiert sein;
  - der Statuscode muss für diese Operation deklariert sein;
  - jeder API-Header, den sie sendet (`Location`, `X-Total-Count`, `Retry-After`), muss für diesen Status deklariert
    sein.

  Ein Verstoß lässt den Test scheitern, der die Antwort erzeugt hat, mit Nennung der Operation und der Lücke. Die
  Prüfung wurde bestätigt, indem der Header `Location`, der Status `409` und `X-Total-Count` nacheinander aus der YAML
  gelöscht wurden; sie hat jeden Fall erkannt (18, 8 und 4 fehlschlagende Tests). Antworten, die MockMvc nicht erzeugen
  kann (`413`, das der Servlet-Container durchsetzt), wurden gegen die gepackte JAR geprüft.

Es gibt diese Prüfung, weil einmal drei Punkte durchgerutscht sind (`Location` bei Buchungen, `413`, die Länge der
Kundennummer): Die statische Prüfung vergleicht nur Pfade und Feldnamen.

(springdoc würde OpenAPI 3 erzeugen, die Spezifikation ist aber Swagger 2.0.)

**Inklusive Filter:** Die Spezifikation sagt nur *"Beginn des Abfragezeitraums inklusive"* / *"Ende des
Abfragezeitraums inklusive"*, nicht inklusive wovon. `from` und `to` der Terminliste sind inklusiv und wählen jeden
Termin, der zumindest teilweise im Zeitraum läuft (`end > from AND start ≤ to`). Das meinen Mitarbeiter
üblicherweise mit „Termine am Montag“.
