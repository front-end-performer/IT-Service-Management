# IT-Service-Termine

Dies ist eine Lösung der [IT-Service-Management-Coding-Challenge](docs/task/README.de.md) mit Spring-Boot-Backend und
Ext-JS-Frontend. Kunden finden und buchen Termine im 15-Minuten-Raster bei IT-Service-Anbietern. Die Anwendung lässt
die parallele Kapazität eines Anbieters niemals überschreiten, auch nicht bei gleichzeitig eintreffenden Anfragen.
Bestandstermine lassen sich aus CSV-Dateien importieren.

| | |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, ausführbare JAR |
| Datenbank | PostgreSQL (Standard, über `docker-compose.yml`) oder H2 (Profil `h2`, ohne Einrichtung) |
| Frontend | Ext JS 6.2 (GPL, Classic Toolkit), vom Backend ausgeliefert unter `http://127.0.0.1:8080/` |
| API | [`rest-api-spec.yml`](rest-api-spec.yml) (Swagger 2.0, Original-Spezifikation plus dokumentierte Erweiterungen) |
| Entscheidungen | [`docs/adr/`](docs/adr) |
| Abweichungen von Aufgabe und Spezifikation | [`docs/deviations.md`](docs/deviations.md): jede Abweichung mit Begründung, ADR und Test |
| Anforderungen → Tests | [`docs/test-coverage.md`](docs/test-coverage.md) |
| Einsatz von KI | [`docs/ai-usage.md`](docs/ai-usage.md) |

## Voraussetzungen

- **JDK 21+** (ein vollständiges JDK, da zum Bauen `javac` nötig ist). Maven ist nicht nötig, der Wrapper `./mvnw` ist
  enthalten.
- **Docker**, für PostgreSQL und die Integrationstests (Testcontainers).

## Bauen und starten

```bash
# 1. PostgreSQL starten (Datenbank/Benutzer/Passwort: icares, Port 5432)
docker compose up -d

# 2. Ausführbare JAR bauen (führt alle Tests aus; mit -DskipTests überspringen)
./mvnw clean package

# 3. Starten
java -jar target/appointments-1.0.0.jar
```

Danach **http://127.0.0.1:8080/** öffnen.

**Fertige JAR:** GitHub Actions ([`.github/workflows/build.yml`](.github/workflows/build.yml)) baut und testet jeden
Push mit demselben `./mvnw verify` und PostgreSQL über Testcontainers.
- **Bei jedem Lauf** wird die ausführbare JAR als Build-Artefakt `appointments-jar` angehängt.
- **Bei Versions-Tags** wie `v1.0.0` wird zusätzlich ein GitHub-Release mit der JAR veröffentlicht.

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Beim Start legt Flyway das Schema an, und der Anbieter- und Dienstleistungskatalog aus `application.yml` wird in die
Datenbank übernommen.

**Port 5432 schon belegt?** Ist PostgreSQL lokal installiert, belegt es meist Port 5432.
- **Was passiert:** `docker compose up -d` kann den Container nicht starten („address already in use“), und die JAR
  verbindet sich stattdessen mit dem *lokalen* PostgreSQL. Sie scheitert dann mit
  `FATAL: password authentication failed for user "icares"`.
- **Lösung:** beide mit demselben `DB_PORT` auf einen anderen Port legen:

```bash
DB_PORT=55432 docker compose up -d
DB_PORT=55432 java -jar target/appointments-1.0.0.jar
```

**Ohne Docker:** die eingebettete H2-Dateidatenbank verwenden. Die Daten landen in `./data/`.

```bash
./mvnw clean package -DskipTests
java -jar target/appointments-1.0.0.jar --spring.profiles.active=h2
```

Andere Datenbankeinstellungen lassen sich wie üblich übergeben, z. B.
`--spring.datasource.url=jdbc:postgresql://host:5432/db`.

## Bestandstermine importieren

Der Anbieter wird **immer ausdrücklich gewählt**, entweder in der URL oder im Tab *Import CSV* der Oberfläche. Er wird
nie aus dem Dateinamen abgeleitet.

> ⚠️ **Die beiden Dateien in `termine/` haben vertauschte Namen.** `Computer-Service-Schneider.csv` enthält DIA/LAP/OSI,
> also den Katalog von IT-Service Müller. `IT-Service-Mueller.csv` enthält DIA/VIR/DAT, also den Katalog von Schneider.
> Importieren Sie sie nach Inhalt:

```bash
curl -F file=@termine/Computer-Service-Schneider.csv http://127.0.0.1:8080/it-services/IT-MUELLER/appointment-imports
curl -F file=@termine/IT-Service-Mueller.csv         http://127.0.0.1:8080/it-services/CS-SCHNEIDER/appointment-imports
```

Jeder Import übernimmt 20 von 20 Zeilen. Importiert man eine Datei stattdessen in den Anbieter aus ihrem Dateinamen,
werden die fremden Leistungscodes abgelehnt und im Bericht aufgeführt. Die nach Schneider benannte Datei in
`CS-SCHNEIDER` ergibt zum Beispiel 11 importierte Zeilen (DIA) und 9 abgelehnte (`UNKNOWN_SERVICE_CODE`: LAP, OSI),
jeweils mit Zeilennummer. Ein erneuter Import derselben Datei fügt nichts hinzu: Jede Zeile wird als `DUPLICATE`
gemeldet.

Ablehnungsgründe: `MALFORMED_ROW`, `MISSING_FIELD`, `INVALID_CUSTOMER_NUMBER`, `INVALID_DATE`, `OFF_GRID`,
`OUTSIDE_OPENING_HOURS`, `UNKNOWN_SERVICE_CODE`, `CAPACITY_EXCEEDED`, `DUPLICATE`. Jeder Import hat einen Status:
`COMPLETED`, oder `FAILED` mit der Zeile, an der er abgebrochen ist. Eine abgebrochene Datei erneut zu importieren ist
sicher. Berichte werden gespeichert und lassen sich über
`GET /it-services/{id}/appointment-imports[/{importId}]` erneut abrufen.

## Die API im Überblick

`serviceId` bezeichnet in der Spezifikation den **Anbieter**, `repairServiceId` den **Leistungscode**. Die Benennung der
Spezifikation bleibt unverändert.

```bash
H=http://127.0.0.1:8080
curl $H/it-services                                    # Anbieter: IT-MUELLER, CS-SCHNEIDER
curl $H/it-services/IT-MUELLER/services                # DIA 30 min, LAP 120 min, OSI 60 min
curl "$H/it-services/IT-MUELLER/appointment-suggestions?repairServiceId=LAP&from=2026-11-02T08:00:00Z&to=2026-11-02T13:00:00Z"
curl -i -H 'Content-Type: application/json' \
     -d '{"customerNumber":"10001","start":"2026-11-02T09:45:00Z","repairServiceId":"LAP"}' \
     $H/it-services/IT-MUELLER/appointments           # 201 + Location; 409, wenn der Termin vergeben ist
curl "$H/it-services/IT-MUELLER/appointments?from=2026-11-02T00:00:00Z&to=2026-11-02T23:59:59Z&repairServiceId=LAP"
curl $H/it-services/IT-MUELLER/appointments/{appointmentId}
```

Das Paging der Terminliste ist optional. Ohne `page`/`size` kommen alle Termine, wie spezifiziert. Mit `page` (ab 0)
und/oder `size` (1–500, Standard 100) kommt eine Seite. `X-Total-Count` enthält immer die Gesamtzahl.

Fehler werden als RFC-9457-Problem-Details geliefert: `400` bei ungültigen Eingaben oder einem Start außerhalb des
Rasters, `404` bei unbekanntem Anbieter, Dienst oder Termin und `409`, wenn keine Kapazität mehr frei ist oder die
Buchung ein Duplikat ist.

Alle Zeiten sind UTC-`Instant`s. Eingaben dürfen einen beliebigen Offset haben und Sekunden weglassen
(`2026-11-02T10:00+01:00`, `2026-11-02T08:00Z`). Ausgaben sind immer `…Z`. Die Oberfläche rechnet in die Zeitzone des
Anbieters um, die die API je Anbieter liefert (`timeZone`, `openingHours`). Hinweis: 08:00Z ist im November 09:00 in
Deutschland (MEZ).

## Terminregeln

- Ein Termin beginnt auf einer 15-Minuten-Grenze (`:00/:15/:30/:45`), und `Ende = Start + Dauer der Dienstleistung des
  Anbieters`.
- Intervalle sind halboffen, `[Start, Ende)`. Ein Termin, der um 09:00 endet, überschneidet sich nicht mit einem, der um
  09:00 beginnt.
- Ein Termin ist frei, wenn die **höchste** Zahl parallel laufender Termine während seiner **gesamten** Dauer unter der
  Kapazität bleibt. Gebuchte und importierte Termine zählen gleich.
- Neue Buchungen dürfen nicht in der Vergangenheit beginnen (400), und Vorschläge enthalten nie vergangene Termine.
  Importierte Bestandstermine dürfen in der Vergangenheit liegen.
- Dauern müssen keine Vielfachen von 15 Minuten sein. Nur die Startzeiten liegen im Raster; die Kapazitätsprüfung
  rechnet mit exakten Intervallen.
- Termine müssen vollständig in den **Öffnungszeiten** des Anbieters liegen (Ortszeit `Europe/Berlin`, Sommerzeit
  berücksichtigt). Sie dürfen genau zur Schließzeit enden, aber nicht darüber hinaus und nicht über Mitternacht. Müller
  hat Mo–Fr 08:00–18:00 und Sa 09:00–13:00 geöffnet, Schneider Mo–Fr 08:00–17:00 und Sa 10:00–14:00. Das ist eine
  dokumentierte Abweichung, siehe [ADR 0007](docs/adr/0007-opening-hours.md).
- Vorschläge sind alle Rasterstartzeiten in `[from, to]`, deren Dienstleistung bis `to` beendet ist, in den
  Öffnungszeiten liegt und passt. Der Suchzeitraum ist auf 31 Tage begrenzt.
- Überbuchung bei parallelen Anfragen verhindert eine Zeilensperre auf dem Anbieter während der erneuten Prüfung und
  des Einfügens, für Buchungen wie für Importe. Wer das Rennen verliert, erhält 409. Das Warten auf die Sperre ist auf
  PostgreSQL und H2 gleich begrenzt (`appointments.lock-timeout-ms`, 10 s). Wird die Grenze erreicht, erhält die Anfrage
  503 + Retry-After. Siehe [ADR 0003](docs/adr/0003-pessimistic-provider-lock.md).

## Anbieter oder Dienstleistung hinzufügen

`catalog.providers` in [`application.yml`](src/main/resources/application.yml) bearbeiten, oder per externer
`application.yml` bzw. Umgebungsvariablen überschreiben, dann neu starten. Es sind keine Codeänderungen nötig.

```yaml
catalog:
  providers:
    - id: PC-HILFE-WEBER
      name: PC-Hilfe Weber
      capacity: 4
      time-zone: Europe/Berlin
      opening-hours:            # fehlende Tage sind geschlossen; ganz weglassen = durchgehend geöffnet
        MONDAY: 09:00-17:00
        FRIDAY: 09:00-15:00
      services:
        - { code: DIA, name: PC-Diagnose, duration-minutes: 20 }
```

## Tests

```bash
./mvnw test     # 150 Tests; Integrationstests starten PostgreSQL 17 über Testcontainers (Docker nötig)
```

Die **Abnahme-Suite** (`acceptance/`, 55 Tests) folgt der Aufgabenstellung Szenario für Szenario (C1–C13, G, E, S, B,
K, I, L, P, X1). Zuordnung und Rückverfolgbarkeitsmatrix stehen in [`docs/test-coverage.md`](docs/test-coverage.md).
Die übrigen Tests decken die Grenzfälle des Entwurfs ab:

| Test | Prüft |
|---|---|
| `OpeningHoursTest` | Öffnungszeiten in Ortszeit, geschlossene Tage, Ende genau zur Schließzeit, über die Schließzeit hinaus, nie über Mitternacht, Zeitumstellung im Frühjahr und Herbst |
| `CapacityCalculatorTest`, `SlotFinderTest`, `TimeGridTest` | Überschneidungen, aneinandergrenzende Intervalle, eine Spitze innerhalb der Dauer, Szenarien für Müller (Kapazität 2) und Schneider (Kapazität 3), Rasterrundung |
| `AppointmentApiTest` | Buchung (201/400/404/409), Endzeit je Anbieter, Starts in der Vergangenheit, Zeiten ohne Sekunden, Duplikate, Vorschläge, Filter, Details |
| `ConcurrentBookingTest` | 20 parallele Buchungen eines Termins ergeben genau 2 angelegte; überlappende parallele Buchungen; ein Import im Wettlauf mit Buchungen; identische parallele Anfragen; parallele Neu-Importe |
| `CatalogExtensibilityTest` | Ein neuer Anbieter und eine geänderte Dauer nur per Konfiguration; bestehende Termine behalten ihre Endzeit |
| `H2ProfileTest` | Der gesamte Ablauf einschließlich Zeilensperre und Warten darauf im H2-Profil |
| `LockTimeoutTest` | Ein Sperr-Timeout liefert 503 + Retry-After |
| `FrontendTest` | Die Ext-JS-Anwendung und ihre Laufzeit werden aus der JAR ausgeliefert |
| `SpecContractTest` | Implementierte Endpunkte und DTO-Felder entsprechen genau `rest-api-spec.yml` |
| `SpecConformance` (läuft in jedem API-Test mit) | Jeder Statuscode und API-Header, der in einem Test zurückkommt, ist in `rest-api-spec.yml` deklariert |
| `ImportApiTest`, `CsvAppointmentParserTest` | Echte Bestandsdateien, Import in den falschen Anbieter, erneuter Import, jeder Ablehnungsgrund mit Zeilennummer, Header-Fehler |

Um zu prüfen, dass die Nebenläufigkeitstests tatsächlich greifen, wurde die Sperre entfernt und die Tests erneut
ausgeführt:
- **Buchung ohne Sperre:** 10 von 20 parallelen Buchungen gingen bei Kapazität 2 durch, und der Test schlug fehl.
- **Import ohne Sperre:** der Test „Import im Wettlauf mit Buchungen“ und der Test für parallele Neu-Importe schlugen
  fehl.

Die Zuordnung jeder Anforderung der Aufgabe und jedes bekannten Fallstricks zu ihrem Test steht in
[`docs/test-coverage.md`](docs/test-coverage.md).

## Manuelle Prüfung des Frontends

Die Aufgabe verlangt ein funktionierendes Frontend, keine automatisierten Frontend-Tests. Diese Prüfung nach
`java -jar …` und dem Import der beiden Bestandsdateien (siehe oben) unter http://127.0.0.1:8080/ durchführen. Die
Oberfläche ist englischsprachig; Beschriftungen stehen daher unten im Original.

**Die Oberfläche zeigt und erwartet Zeiten in der Zeitzone des Anbieters** (Europe/Berlin für beide Anbieter), egal in
welcher Zeitzone der Browser läuft. Unter der Anbieterauswahl steht „Times in Europe/Berlin · Opening hours: Mon–Fri
08:00–18:00, Sat 09:00–13:00“. Im November ist Berlin UTC+1, 09:00 auf dem Bildschirm sind also 08:00Z in API und CSV.

| # | Funktion (Aufgabe) | Schritte | Erwartet |
|---|---|---|---|
| 1 | Anbieter auswählen | Tab *Book appointment*, „IT service provider“ öffnen | Müller (Kapazität 2) und Schneider (Kapazität 3) |
| 2 | Dienstleistung auswählen | Müller wählen, „Service“ öffnen | DIA 30 min, LAP 120 min, OSI 60 min |
| 3 | Zeitraum eingeben | Von 2026-11-02 09:00, Bis 2026-11-02 14:00, Dienstleistung LAP | Die Felder übernehmen die Werte |
| 4 | Vorschläge anzeigen | „Find available appointments“ | 10:45, 11:00, … 12:00. Nichts vor 10:45, weil der importierte LAP um 10:00 und DIA um 10:15 die Kapazität 2 ausschöpfen. |
| 5 | Buchen | 10:45 wählen, Kundennummer `C-42`, „Book selected appointment“ | Bestätigung „10:45 – 12:45 (Europe/Berlin)“. Die Liste wird aktualisiert, und 10:45 ist verschwunden. |
| 5b | Erneute Prüfung bei der Buchung | DIA 09:00–18:00 suchen. Bevor auf 09:00 *Book* geklickt wird, den Termin von außen füllen, z. B. zweimal `curl -H 'Content-Type: application/json' -d '{"customerNumber":"X","start":"2026-11-02T08:00:00Z","repairServiceId":"DIA"}' http://127.0.0.1:8080/it-services/IT-MUELLER/appointments` mit zwei Kundennummern (08:00Z = 09:00 Berlin). Dann *Book* klicken. | „Booking failed: No capacity left for DIA at 2026-11-02 09:00 (Europe/Berlin)“ (409). Der Server meldet UTC, die Oberfläche zeigt die Zeit des Anbieters. Die Liste wird ohne 09:00 aktualisiert. |
| 6 | Termine eines Anbieters in einer Tabelle | Tab *Provider appointments*, Müller, Von/Bis 2026-11-02, Dienstleistung LAP, „Search“ | Zwei Zeilen: 10:00 10003 (IMPORTED) und 10:45 C-42 (BOOKED). Die untere Leiste zeigt „1–2 of 2“ und die Zeitzone. |
| 7 | Details eines Termins | Eine Zeile anklicken | Der Detailbereich zeigt ID, Kunde, Start und Ende (mit Zeitzone), Anbieter, Dienstleistung und Herkunft |

Die Liste ist seitenweise (100 pro Seite); bei mehr Terminen blättern die Pfeile in der unteren Leiste.

Außerdem den Tab *Import CSV* prüfen: `termine/IT-Service-Mueller.csv` in Müller importiert listet abgelehnte Zeilen mit
Zeilennummer und Grund (`UNKNOWN_SERVICE_CODE` für VIR/DAT).

Diese gesamte Prüfung einschließlich 5b, Paging und Import lief zuletzt per Headless-Chrome gegen die gepackte JAR, mit
auf America/New_York gestelltem Browser (um zu belegen, dass die Zeitzone des Anbieters gilt) und ohne Fehler in der
Browser-Konsole (siehe [`docs/ai-usage.md`](docs/ai-usage.md)).

## Projektaufbau

```
src/main/java/de/challenge/appointments/
  scheduling/   reine Domäne: TimeSlot, TimeGrid, CapacityCalculator, SlotFinder (ohne Spring, ohne DB)
  catalog/      Anbieter und Dienstleistungen, YAML-Konfiguration, Abgleich beim Start, CatalogService (alle Abfragen und die Anbietersperre)
  appointment/  Entität Appointment, AppointmentScheduler (einziger Schreibpfad), Buchungs-, Vorschlags- und Abfrage-Services
  imports/      CSV-Parser, Importläufe, Ablehnungsgründe
  api/          REST-Controller, DTOs, Fehlerabbildung nach RFC 9457
src/main/resources/
  db/migration/ Flyway-Schema
  static/       Ext-JS-Anwendung (index.html, app/) und die mitgelieferte Ext-JS-6.2.0-GPL-Laufzeit
docs/adr/       Architecture Decision Records
docs/ai/        mit KI-Unterstützung erstellter Implementierungsplan
```
