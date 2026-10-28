# 0003 – Überbuchung wird durch eine pessimistische Sperre je Anbieter verhindert

**Status:** angenommen

## Kontext

Kapazität ist eine Eigenschaft eines Anbieters und eines Zeitraums: „höchstens N Termine gleichzeitig, zu jedem
Zeitpunkt“. Zwei gleichzeitige Buchungen können beide „N−1 laufen“ lesen und beide einfügen. Das ist eine klassische
Write-Skew-Anomalie, die `READ COMMITTED` nicht verhindert. Ein Unique- oder Exclusion-Constraint kann „höchstens N
überlappende Zeilen“ für N > 1 nicht ausdrücken.

## Entscheidung

Jeder Schreibvorgang am Terminplan eines Anbieters (Buchung und CSV-Import) läuft in einer Transaktion, die

1. die Anbieterzeile mit `SELECT … FOR UPDATE` lädt (`ProviderRepository.findByIdForUpdate`,
   `@Lock(PESSIMISTIC_WRITE)`, aufgerufen über `CatalogService.lockProvider`),
2. die Termine des Anbieters lädt, die das Kandidatenintervall überlappen, und die Spitzenlast prüft
   (`CapacityCalculator`),
3. den Termin einfügt und committet, wodurch die Sperre freigegeben wird.

Alle Schreibvorgänge laufen über `AppointmentScheduler.schedule`, annotiert mit
`@Transactional(propagation = MANDATORY)`; ohne umgebende Transaktion lässt es sich nicht aufrufen. Vorschläge werden
ohne Sperre gelesen. Sie sind eine Momentaufnahme, und ein inzwischen vergebener Termin lässt die Buchung mit **409**
scheitern.

### Betrachtete Alternativen

| Option | Warum nicht |
|---|---|
| Isolationsstufe `SERIALIZABLE` | Auf PostgreSQL korrekt (SSI), braucht aber Wiederholungslogik bei Serialisierungsfehlern, und H2 verhält sich anders |
| Optimistische `@Version` am Anbieter, bei jeder Buchung erhöht | Gleichwertige Serialisierung, braucht aber Wiederholungen; die Konkurrenz je Anbieter ist so oder so gering |
| PostgreSQL-Exclusion-Constraint | Drückt nur Kapazität 1 aus |
| Zählertabelle je 15-Minuten-Slot mit Zeilensperren oder Check-Constraints | Funktioniert für Kapazität N, fügt aber eine redundante Datenstruktur hinzu, die mit den Dauern synchron gehalten werden muss |
| `synchronized` oder Sperr-Map in der JVM | Funktioniert nicht mehr bei mehr als einer Anwendungsinstanz |

### Warten auf die Sperre: gleich auf PostgreSQL und H2

- **Unterschiedliche Standards:** PostgreSQL wartet unbegrenzt auf eine Zeilensperre, H2 gibt nach etwa 2 Sekunden
  auf (`Timeout trying to lock table`). Gemessen mit einem Test, der die Sperre 5 s hielt.
- **Eine ausdrückliche Grenze:** Beide Datenbanken erhalten deshalb dieselbe Grenze, `appointments.lock-timeout-ms`
  (Standard 10 s). Sie wird auf jeder Pool-Verbindung über `spring.datasource.hikari.connection-init-sql` gesetzt, in
  der jeweiligen Syntax: `SET lock_timeout = '…ms'` in PostgreSQL, `SET LOCK_TIMEOUT …` in H2.
- **Wird die Grenze erreicht,** erhält die Anfrage **503** mit `Retry-After: 1`, kein 500 und auch kein 409. Ein
  Timeout sagt nichts über die Kapazität aus: Der Termin kann durchaus frei sein. Unter normaler Last passiert das nie,
  weil eine Buchung die Sperre nur Millisekunden hält.

## Konsequenzen

- Korrekt über mehrere Anwendungsinstanzen hinweg, weil die Sperre in der Datenbank liegt. Funktioniert auf PostgreSQL
  und H2.
- Buchungen für **denselben Anbieter** werden serialisiert. Eine Buchung dauert Millisekunden, der Durchsatz je Anbieter
  liegt also weit über dem Bedarf eines IT-Service-Betriebs. Verschiedene Anbieter blockieren sich nicht.
- **Tests auf beiden Datenbanken** prüfen das Verhalten; für jeden wurde bestätigt, dass er ohne Sperre fehlschlägt:
  - `ConcurrentBookingTest` (PostgreSQL): parallele Buchungen, ein Import im Wettlauf mit Buchungen, identische
    Anfragen, parallele Neu-Importe. Ohne Sperre gingen bei Kapazität 2 10 von 20 parallelen Buchungen durch.
  - `ConcurrentBookingTest.bookingAndImportWaitForTheProviderLockInsteadOfFailing`: Solange eine andere Transaktion die
    Sperre hält, **warten** Buchung und Import darauf. Das belegt, dass sie die Sperre tatsächlich nehmen. Der
    Gegenfall `otherProvidersAreNotBlocked` zeigt, dass eine Buchung bei einem anderen Anbieter nicht wartet.
  - `AppointmentApiTest.parallelHttpRequestsGet201UntilCapacityThen409`: Auf HTTP-Ebene erhalten die Verlierer des
    Wettlaufs **409**, und genau so viele Anfragen wie die Kapazität erhalten 201.
  - `H2ProfileTest`: dieselben Prüfungen für parallele Buchungen und das Warten auf die Sperre auf H2. Ohne Sperre hat
    auch H2 überbucht (3 bei Kapazität 2).
  - `LockTimeoutTest`: Ein Sperr-Timeout wird zu 503 + Retry-After.
- Der Unique-Key `(provider, service, customer, start)` ist ein zweites Sicherheitsnetz gegen doppelt abgeschickte
  Buchungen. Er wird auf 409 abgebildet.
