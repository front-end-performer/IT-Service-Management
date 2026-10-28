> **Hinweis:** Dies ist der Plan, wie er vor der Implementierung freigegeben wurde (mit Claude Code im Planmodus
> erstellt; ursprünglich auf Englisch, hier übersetzt). Er ist ein historisches Dokument und weicht an diesen Stellen
> vom Ergebnis ab:
> - **Spring-Boot-Version:** 4.1.2 statt 3.5.x, weil der Spring Initializr 3.5 nicht mehr anbietet.
> - **Import beim Start:** Das optionale `import.on-startup` entfiel als unnötig (YAGNI). Stattdessen den
>   REST-Endpunkt oder die Oberfläche verwenden.
> - **Kopie der Spezifikation:** Es gibt keine Kopie unter `static/`. Maßgeblich ist allein `rest-api-spec.yml` im
>   Projektverzeichnis.
> - **Später ergänzt** (siehe [ai-usage.md](../ai-usage.md)): unter anderem Öffnungszeiten, Importstatus, Import in
>   Abschnitten und optionales Paging; die Dienstleistungsklasse heißt heute `ServiceOffering` statt `RepairService`.

# Plan: Terminplanung für IT-Services (Coding-Challenge)

## Kontext
Neues Projekt. Vorhandene Eingaben:
`README.md` (deutsche Aufgabe), `rest-api-spec.yml` (Swagger 2.0), `docker-compose.yml` (postgres:11-alpine,
DB/Benutzer/Passwort `icares`), `termine/IT-Service-Mueller.csv`, `termine/Computer-Service-Schneider.csv` (je 20 Zeilen).
Ziel: Spring-Boot-Backend (ausführbare JAR) + Ext-JS-Frontend zum Finden und Buchen von Terminen im
15-Minuten-Raster, ohne die parallele Kapazität eines Anbieters je zu überschreiten, auch bei gleichzeitigen Anfragen,
plus CSV-Import von Bestandsterminen.

Werkzeuge auf dem Rechner: Java 21 (verwenden), Node 24, Docker. Kein Maven installiert → Maven-Wrapper (`mvnw`)
einchecken.

**Befund (Falle in den Daten):** Die Namen der CSV-Dateien sind gegenüber ihrem Inhalt vertauscht.
`Computer-Service-Schneider.csv` enthält DIA/LAP/OSI (= Katalog von Müller); `IT-Service-Mueller.csv` enthält
DIA/VIR/DAT (= Katalog von Schneider). Folge: Der Anbieter wird nie aus dem Dateinamen abgeleitet. Die Importanfrage
nennt den Anbieter ausdrücklich, und jede Zeile mit unbekanntem Leistungscode wird abgelehnt und gemeldet. Eine Datei
in den „falschen“ Anbieter zu importieren ergibt daher einen sichtbaren Bericht (z. B. 11 DIA importiert, 9
abgelehnt). In README und ADR dokumentieren.

## Entscheidungen (abgestimmt)
- Maven, Spring Boot 3.5.x, Java 21, Spring Data JPA, Flyway, Bean Validation.
- PostgreSQL als Standard (docker-compose; Image auf `postgres:17-alpine` anheben, dokumentierte Abweichung);
  `h2`-Profil für einen schnellen Start.
- Keine Öffnungszeiten: Jeder 15-Minuten-Slot in `[from, to]` mit freier Kapazität ist gültig (wörtlich nach
  Spezifikation). Alle Zeiten als UTC-`Instant`.
- Ext JS **6.2.0 GPL Classic, mitgeliefert** in der JAR (`src/main/resources/static/vendor/extjs-6.2.0/`:
  `ext-all.js`, `classic/theme-triton/resources/**`, `packages/font-awesome` / `font-ext`). Quelle: npm
  `extjs-gpl@6.2.0` / cdnjs. Begründung (ADR): Das öffentliche `@sencha/ext` 7.x/8.x auf npm steht unter einer
  Testlizenz; 6.2.0 ist der neueste frei weitergebbare GPL-Build; kein Sencha Cmd nötig.

## Domänenmodell (Tabellen über Flyway `V1__schema.sql`)
- `provider(id varchar PK z. B. IT-MUELLER, name, capacity int >0)`
- `repair_service(id uuid/bigint PK, provider_id FK, code, name, duration_minutes >0, UNIQUE(provider_id, code))`
  — die API veröffentlicht `repairService.id` als auf den Anbieter bezogene ID, z. B. `DIA`, eindeutig je Anbieter
  (passend zum Beispiel der Spezifikation).
- `appointment(id uuid PK, provider_id FK, repair_service_id FK, customer_number, start_at, end_at,
  source ENUM(BOOKED, IMPORTED), import_batch_id FK null, created_at)`, Index `(provider_id, start_at, end_at)`;
  UNIQUE `(provider_id, repair_service_id, customer_number, start_at)` für idempotente Importe.
- `import_batch(id, provider_id, file_name, imported_at, total, imported, rejected)` +
  `import_rejection(batch_id, line_no, raw_line, reason)` → Nachvollziehbarkeit ungültiger Zeilen.

Der Anbieter- und Dienstleistungskatalog ist **Konfiguration**: `application.yml` `catalog.providers[]` (id, name,
capacity, services[code, name, durationMinutes]), gebunden an `@ConfigurationProperties`, beim Start von
`CatalogInitializer` in die DB übernommen. Ein Anbieter mehr = nur Konfiguration. Die Endzeit wird immer berechnet,
`Start + Dauer der Dienstleistung` (zum Abfragen gespeichert, nie vom Client übernommen).

## Kernalgorithmus (Paket `scheduling`, reines Java, ohne Spring — umfassend per Unit-Test geprüft)
- `CapacityCalculator.fits(bestehende Intervalle, Kandidat [s,e), Kapazität)`: halboffene Intervalle; Sweep-Line über
  die Start-/Endereignisse der Termine, die den Kandidaten überlappen → höchste Parallelität im Kandidaten < Kapazität.
  (Ende == nächster Start ist keine Überschneidung.)
- `SlotFinder.suggest(from, to, Dauer, bestehende, Kapazität)`: `from` auf das 15-Minuten-Raster aufrunden, `start`
  durchlaufen, solange `start + Dauer <= to`, Slots behalten, bei denen `fits` gilt. Bestehende Termine einmal laden:
  Termine mit `start < to && end > from`.
- Rasterprüfung: `start` muss Minute % 15 == 0 und null Sekunden/Nanosekunden haben → sonst 400.
- Prüfung des Suchzeitraums: `from < to`, Höchstdauer z. B. 31 Tage → sonst 400.

## Nebenläufigkeit (keine Überbuchung)
`BookingService.book(providerId, request)` in einer `@Transactional`:
1. `providerRepository.findByIdForUpdate(id)` mit `@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE` auf der
   Anbieterzeile serialisiert alle Buchungen und Importe dieses Anbieters (funktioniert auf Postgres und H2).
2. Überlappende Termine laden, `CapacityCalculator.fits` → sonst `CapacityExceededException` → 409.
3. Einfügen. Der Commit gibt die Sperre frei.
Der Import nutzt dieselbe Sperre und dieselbe Prüfung je Zeile. Das ADR dokumentiert die Alternativen (Isolationsstufe
Serializable, Postgres-Exclusion-Constraint – kann keine Kapazität >1 ausdrücken, optimistische Version am Anbieter).

## CSV-Import
- Endpunkt (Erweiterung der Spezifikation, dokumentiert): `POST /it-services/{serviceId}/appointment-imports`,
  Multipart `file` → `201` mit `ImportReport {batchId, total, imported, duplicates, rejected[{line, raw, reason}]}`;
  `GET` liefert die Liste der Läufe.
- Parser: Apache Commons CSV, Header verpflichtend (`KdnNr,APP_DATE,SERVICE`), tolerant gegenüber BOM und
  Leerzeichen.
- Gründe je Zeile: `MISSING_FIELD`, `INVALID_DATE` (`2026-11-02T08:00Z` über `OffsetDateTime`/`Instant` parsen),
  `OFF_GRID`, `UNKNOWN_SERVICE_CODE`, `CAPACITY_EXCEEDED`, `DUPLICATE` (bereits importiert → übersprungen, kein
  Fehler).
- Regel je Zeile: Gültige Zeilen werden importiert, fehlerhafte abgelehnt und gespeichert; die ganze Datei in einer
  Transaktion unter der Sperre.
- Außerdem optionaler Import beim Start: `import.on-startup: [{providerId, path}]` (standardmäßig aus) — das README
  zeigt stattdessen curl.

## REST-API (`rest-api-spec.yml` folgen; nach `src/main/resources/static/rest-api-spec.yml` kopieren)
Controller: `ProviderController` (`GET /it-services`, `/{id}`, `/{id}/services`), `AppointmentController`
(`GET/POST /{id}/appointments`, `GET /{id}/appointments/{appointmentId}`), `SuggestionController`, `ImportController`.
Filter der Liste: `from`, `to` (inklusive Überschneidung), `repairServiceId`, `customerNumber` über JPA Specification.
DTOs als Java-Records mit den Namen der Spezifikation (`serviceId` = Anbieter-ID, `repairServiceId` = Leistungscode —
Benennung der Spezifikation beibehalten, die Verwechslungsgefahr dokumentieren). `@RestControllerAdvice` → RFC 7807
`ProblemDetail` für 400/404/409. Abweichungen von der Spezifikation in `docs/api-deviations.md` auflisten und in die
YAML aufnehmen (Import-Endpunkte, 400 bei GET appointments für ungültigen Zeitraum).

## Frontend (Ext JS 6.2 Classic, `src/main/resources/static/index.html` + `app/*.js`, ohne Build)
`Ext.application` mit Viewport und `tabpanel`:
- **Tab Buchen:** Anbieterauswahl (Store `/it-services`) → Dienstleistungsauswahl (`/it-services/{id}/services`) →
  Von/Bis-Datum und -Uhrzeit → „Termine suchen“ → Grid mit Vorschlägen → Auswahl + Kundennummer → „Buchen“ (POST, 409
  zeigt die Meldung und aktualisiert die Vorschläge).
- **Tab Termine:** Anbieterauswahl, Filter (Von/Bis, Dienstleistung), Grid (Start, Ende, Kunde, Dienstleistung,
  Herkunft), Zeilenauswahl → Detailbereich/-fenster (`GET …/appointments/{id}`).
- **Tab Import:** Anbieterauswahl + Dateiupload → Grid des Berichts mit den abgelehnten Zeilen.
Ausgeliefert aus derselben JAR unter `http://127.0.0.1:8080/`.

## Projektaufbau
```
pom.xml, mvnw, docker-compose.yml, rest-api-spec.yml, README.md
src/main/java/de/.../appointments/
  catalog/ (Provider, RepairService, CatalogProperties, CatalogInitializer, Repositories)
  scheduling/ (TimeGrid, CapacityCalculator, SlotFinder)  ← reine Domäne
  appointment/ (Appointment, AppointmentRepository, BookingService, SuggestionService, AppointmentQueryService)
  imports/ (CsvAppointmentParser, ImportService, ImportBatch, ImportRejection)
  api/ (Controller, DTO-Records, GlobalExceptionHandler)
src/main/resources/ application.yml, application-h2.yml, db/migration/, static/
src/test/java/... + src/test/resources/csv/ (erfundene Testdaten)
docs/adr/0001…, docs/ai-usage.md
```

## Tests
- Unit: `CapacityCalculatorTest` (aneinandergrenzende Intervalle, genau die Kapazität, teilweise Überschneidung mitten
  in der Dienstleistung, lange Dienstleistung über mehrere kurze hinweg), `SlotFinderTest` (Rasterrundung, Grenze am
  Ende, Beispiele Müller Kapazität 2 / Schneider Kapazität 3), `TimeGridTest`, `CsvAppointmentParserTest` (falsches
  Datum, fehlende Spalte, unbekannter Code, BOM).
- Integration (`@SpringBootTest` + Testcontainers Postgres): Buchung im Normalfall, 409 bei voller Kapazität, 400
  außerhalb des Rasters, Ende = Start + Dauer, Import beider echten CSV-Dateien in den richtigen/falschen Anbieter →
  exakte Zahlen, erneuter Import → nur Duplikate, Vorschläge schließen die importierte Last aus.
- **Nebenläufigkeit:** 20 Threads buchen über `CountDownLatch` denselben Slot bei einem Anbieter mit Kapazität 2 →
  genau 2×201, 18×409; dazu eine Prüfung in der DB, dass die höchste Parallelität ≤ Kapazität ist.
- MockMvc/WebMvc-Test für die Form der Fehler-JSON.

## Dokumentation / Lieferumfang
- README: Voraussetzungen, `docker compose up -d`, `./mvnw package`, `java -jar target/*.jar` (und
  `--spring.profiles.active=h2`), curl-Beispiele, Import der beiden CSV-Dateien (mit dem Hinweis auf die vertauschten
  Dateien), Testbefehl.
- ADRs: 0001 Stack & Mitlieferung von Ext JS 6.2 GPL, 0002 konfigurationsgesteuerter Katalog, 0003 pessimistische
  Anbietersperre, 0004 halboffene Intervalle & UTC, 0005 zeilenweise Importregeln, 0006 API-Abweichungen.
- `docs/ai-usage.md`: Claude Code für Planung, Implementierung und Review; Plandatei, Prompts/Arbeitsablauf, was vom
  Menschen geprüft wurde.
- `git init` + Commits je Meilenstein (nur auf Anfrage).

## Reihenfolge der Implementierung
1. Maven-Projekt anlegen (Spring Initializr per curl), Wrapper, Compose-Anhebung, Anwendungskonfiguration.
2. Reiner Planungskern + Unit-Tests.
3. Entitäten, Flyway, Katalogkonfiguration + Initialisierung.
4. Buchungs-, Vorschlags- und Abfrage-Services + Sperre, REST + Fehlerbehandlung.
5. CSV-Import + Berichte.
6. Integrations- und Nebenläufigkeitstests.
7. Ext JS 6.2.0 einbinden, Oberfläche bauen.
8. README, ADRs, Dokument zum KI-Einsatz.

## Überprüfung
- `./mvnw verify` (Unit- und Testcontainers-Integrationstests grün).
- `docker compose up -d && ./mvnw package && java -jar target/appointments-*.jar`.
- curl: `GET /it-services` → 2 Anbieter; `termine/Computer-Service-Schneider.csv` in `IT-MUELLER` importieren → 20
  importiert; `GET …/appointment-suggestions?repairServiceId=LAP&from=2026-11-02T08:00:00Z&to=2026-11-02T12:00:00Z`
  schließt belegte Slots aus; zweimal in die letzte freie Kapazität buchen → 201, dann 409; paralleler
  `xargs -P 10`-curl-Ansturm → keine Überbuchung.
- `http://127.0.0.1:8080/` öffnen und die Abläufe Buchen / Liste / Details / Import von Hand durchgehen (oder per
  `run`-Skill + Browser).
