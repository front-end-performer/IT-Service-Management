# 0005 – CSV-Import: ausdrücklicher Anbieter, Prüfung je Zeile, nachvollziehbarer Bericht

**Status:** angenommen

## Kontext

Bestandsdateien haben das Format `KdnNr,APP_DATE,SERVICE` und enthalten keinen Anbieter. Jede Datei muss einem Anbieter
„eindeutig zugeordnet“ werden, und „unbekannte Leistungscodes und ungültige Datensätze“ müssen nachvollziehbar
behandelt werden. Die beiden Beispieldateien haben **vertauschte Namen**: `Computer-Service-Schneider.csv` enthält
Müllers Dienstleistungen und umgekehrt.

*Die Aufgabe sagt:*
- *"Der Importprozess muss unbekannte Leistungscodes und ungültige Datensätze erkennen und nachvollziehbar
  behandeln."*
- *"Die Regeln gelten sowohl für neu gebuchte als auch für importierte Termine."*

Die Aufgabe sagt nicht, ob eine Datei mit fehlerhaften Zeilen teilweise oder gar nicht importiert wird. Sie sagt auch
nicht, welche Zeile gewinnt, wenn Zeilen kollidieren. Beides sind daher unsere Entscheidungen (unten).

## Entscheidung

- **Zuordnung zum Anbieter:** Der Anbieter ist Teil der Anfrage (`POST /it-services/{id}/appointment-imports` bzw. die
  Anbieterauswahl in der Oberfläche). Er wird nie aus dem Dateinamen abgeleitet, dem man, wie die Beispiele zeigen,
  nicht trauen kann.
- **Prüfung nach denselben Regeln wie Buchungen:** Jede Zeile wird einzeln geparst und dann über
  `AppointmentScheduler` eingeplant. Sie wird also gegen dieselben Raster-, Duplikat- und Kapazitätsregeln geprüft wie
  eine Buchung, unter derselben Anbietersperre (siehe 0003). Importierte Termine zählen deshalb sofort für die
  Verfügbarkeit.
- **Teilimport:** Gültige Zeilen werden importiert, ungültige festgehalten. Eine fehlerhafte Zeile blockiert keine
  hundert gültigen, und der Bericht sagt genau, was zu korrigieren ist.
- **Ablehnungsgründe:** `MALFORMED_ROW`, `MISSING_FIELD`, `INVALID_CUSTOMER_NUMBER` (länger als 64 Zeichen),
  `INVALID_DATE`, `OFF_GRID`, `OUTSIDE_OPENING_HOURS`, `UNKNOWN_SERVICE_CODE` (die Meldung nennt die Codes, die der
  Anbieter tatsächlich anbietet) und `CAPACITY_EXCEEDED`. Die Bestandsdaten könnten Überbuchungen enthalten, und die
  übernehmen wir nicht stillschweigend. Die Zeilen werden in Dateireihenfolge verarbeitet.
- **Idempotenz:** Eine Zeile mit demselben Anbieter, Dienst, Kunden und Start wird als `DUPLICATE` gemeldet und
  übersprungen. Ein Import lässt sich gefahrlos wiederholen.
- **Nachvollziehbarkeit:** Ein `import_batch` speichert Dateiname, Zeitpunkt und Zähler. `import_rejection` speichert
  Zeilennummer (Header = Zeile 1), Rohzeile, Grund und Meldung. Jeder importierte Termin trägt `source = IMPORTED` und
  seine `import_batch_id`.
- **Fehler auf Dateiebene:** Ein Dateiname ohne Endung `.csv` (Groß-/Kleinschreibung egal), eine leere Datei, ein fehlender oder fremder Header, doppelte Spaltennamen oder kaputte
  Anführungszeichen ergeben `400`, und nichts wird importiert. Der Parser akzeptiert ein UTF-8-BOM, CRLF-Zeilenenden,
  Leerzeilen und umgebende Leerzeichen.

## Konsequenzen

- Wird der falsche Anbieter gewählt, listet der Bericht die fremden Leistungscodes auf, statt sie unter dem falschen
  Anbieter zu importieren. Gemeinsame Codes wie DIA würden trotzdem mit der Dauer dieses Anbieters importiert. Ohne
  eine Anbieterspalte in der Datei ist das nicht vermeidbar, und es ist im Bericht sichtbar.
- **Verarbeitung in Abschnitten:** Ein Import läuft in Abschnitten von `appointments.import-chunk-size` Zeilen
  (Standard 100). Jeder Abschnitt ist eine eigene Transaktion unter der Anbietersperre; eine große Datei blockiert
  Buchungen dieses Anbieters also nie länger als einen Abschnitt.
  - Die Zeilen werden weiterhin in Dateireihenfolge nach denselben Regeln geprüft, und die Ergebnisse hängen nicht von
    den Abschnittsgrenzen ab (`ChunkedImportTest`).
  - Eine Buchung, die zwischen zwei Abschnitte fällt, zählt wie jeder andere bestehende Termin; spätere Zeilen können
    dann wegen der Kapazität abgelehnt werden. Das entspricht der Regel „wer zuerst kommt, mahlt zuerst“ bei Buchungen.
  - **Importstatus:** Jeder Importlauf hat einen Status: `RUNNING`, solange Abschnitte verarbeitet werden, danach
    `COMPLETED` (jede Zeile verarbeitet) oder `FAILED` (Flyway `V3`).
  - **Bei einem unerwarteten Fehler in einem Abschnitt** (z. B. einem Datenbankfehler):
    - wird nur dieser Abschnitt zurückgerollt;
    - wird der Lauf als `FAILED` markiert, mit einer `failureMessage`, die die erste nicht verarbeitete Zeile nennt und
      festhält, dass ein erneuter Import sicher ist;
    - erhält der Client **500** mit `importId` und einer `Location` zum Bericht;
    - werden interne Details (SQL, Stacktraces) auf dem Server protokolliert, nicht zurückgegeben.
  - **Bei einem Sperr-Timeout** wird der Lauf ebenfalls als `FAILED` markiert, und der Client erhält **503** +
    `Retry-After`, weil dieser Fall wiederholbar ist.
  - **Warum der Status wichtig ist:** Ohne ihn sähe ein abgebrochener Import aus wie ein normaler Import mit weniger
    Zeilen, und das wäre nicht „nachvollziehbar“. Abgeschlossene Abschnitte bleiben importiert, und die Datei erneut zu
    importieren ist sicher, weil bereits importierte Zeilen als Duplikate gemeldet werden.
  - **Grenze:** Ist die Datenbank selbst nicht erreichbar, lässt sich auch die Markierung `FAILED` nicht schreiben, und
    der Lauf bleibt auf `RUNNING`. Dieser Zustand ist in der Importliste und im Server-Log sichtbar.
