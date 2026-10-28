
# IT-Service-Management-Coding Challenge

## Aufgabenstellung

Unser Kunde betreibt mehrere IT-Service-Unternehmen und hat uns beauftragt, eine Software zur Verwaltung von IT-Dienstleistungen und zur Planung von Kundenterminen zu entwickeln.

Die Software soll Kunden ermöglichen, passende Termine für unterschiedliche IT-Services zu finden und zu buchen. Gleichzeitig sollen IT-Service-Anbieter ihre angebotenen Leistungen, verfügbaren Kapazitäten und vereinbarten Termine verwalten können.

Die Anwendung soll zunächst für zwei IT-Service-Anbieter entwickelt werden. Nach einer erfolgreichen Testphase soll die Lösung auf weitere Anbieter erweiterbar sein, ohne dass grundlegende Änderungen am Quellcode erforderlich sind.

Aus einem bestehenden Altsystem sollen bereits vereinbarte Termine über CSV-Dateien importiert werden können.

## Fachlicher Kontext

Jeder IT-Service-Anbieter verfügt über eine eigene Kapazität und bietet unterschiedliche IT-Dienstleistungen an.

Das Leistungsspektrum kann beispielsweise folgende Bereiche umfassen:

- **IT-Support:** Unterstützung bei technischen Problemen und Benutzeranfragen
- **Computer- und Laptop-Service:** Diagnose, Wartung und Reparatur von Endgeräten
- **Software und Betriebssysteme:** Installation, Konfiguration und Fehlerbehebung
- **IT-Sicherheit:** Virenentfernung und Behebung sicherheitsbezogener Probleme
- **Datenmanagement:** Datenrettung und Unterstützung bei Datenproblemen

Jede Dienstleistung besitzt einen eindeutigen Leistungscode und eine definierte Bearbeitungsdauer. Je nach Anbieter können dieselben Leistungen unterschiedliche Bearbeitungszeiten haben.

**Die verfügbare Kapazität eines IT-Service-Anbieters darf zu keinem Zeitpunkt überschritten werden.**

### IT-Service-Anbieter 1: IT-Service Müller

Maximal zwei parallel laufende Termine.

| Leistungscode | IT-Service | Dauer |
|---|---|---|
| DIA | PC-Diagnose | 30 Minuten |
| LAP | Laptop-Service | 2 Stunden |
| OSI | Betriebssysteminstallation | 1 Stunde |

### IT-Service-Anbieter 2: Computer-Service Schneider

Maximal drei parallel laufende Termine.

| Leistungscode | IT-Service | Dauer |
|---|---|---|
| DIA | PC-Diagnose | 15 Minuten |
| VIR | Virenentfernung | 1 Stunde |
| DAT | Datenrettung | 3 Stunden |

Die Leistungsangebote, Bearbeitungszeiten und Kapazitäten sollen konfigurierbar sein, damit zukünftig weitere IT-Service-Anbieter und Dienstleistungen hinzugefügt werden können.

## Funktionale Anforderungen

### 1. IT-Service-Anbieter verwalten

Die Anwendung muss mehrere IT-Service-Anbieter unterstützen.

Für jeden Anbieter sollen mindestens folgende Informationen verwaltet werden:

- Eindeutige Kennung
- Name des Anbieters
- Maximale Anzahl parallel bearbeitbarer Termine
- Angebotene IT-Dienstleistungen

### 2. IT-Dienstleistungen verwalten

Jeder Anbieter kann mehrere IT-Dienstleistungen anbieten.

Eine Dienstleistung umfasst mindestens:

- Eindeutige Kennung
- Leistungscode
- Bezeichnung
- Bearbeitungsdauer in Minuten

Die angebotenen Leistungen können sich zwischen Anbietern unterscheiden. Auch bei identischen Leistungscodes können unterschiedliche Bearbeitungszeiten gelten.

### 3. Termine vorschlagen

Kunden sollen für einen ausgewählten IT-Service-Anbieter und eine bestimmte Dienstleistung verfügbare Termine erhalten.

Bei der Berechnung müssen folgende Faktoren berücksichtigt werden:

- Die Dauer der ausgewählten Dienstleistung
- Bereits vereinbarte Termine
- Aus CSV-Dateien importierte Termine
- Die maximale parallele Kapazität des Anbieters
- Das vorgegebene 15-Minuten-Zeitintervall

Es dürfen ausschließlich Termine vorgeschlagen werden, bei denen die Kapazität während der gesamten Dienstleistungsdauer ausreicht.

### 4. Termine buchen

Kunden sollen einen vorgeschlagenen Termin buchen können.

Vor der Buchung muss die Verfügbarkeit erneut geprüft werden. Die Buchung und Kapazitätsprüfung müssen so umgesetzt werden, dass auch bei gleichzeitig eintreffenden Anfragen keine Überbuchung entsteht.

Die Endzeit eines Termins wird anhand der konfigurierten Dauer der gewählten Dienstleistung berechnet.

### 5. Termine verwalten

Mitarbeiter eines IT-Service-Anbieters sollen:

- Alle Termine ihres Anbieters einsehen können
- Termine nach Zeitraum filtern können
- Termine nach Dienstleistung filtern können
- Details eines ausgewählten Termins abrufen können

Authentifizierung und Autorisierung sind nicht zwingend Bestandteil dieser Aufgabe.

### 6. Bestehende Termine importieren

Die Anwendung muss bestehende Termine aus CSV-Dateien importieren können.

Die CSV-Dateien verwenden folgendes Format:

```csv
KdnNr,APP_DATE,SERVICE
10001,2026-11-02T08:00Z,DIA
10002,2026-11-02T08:30Z,DIA
10003,2026-11-02T09:00Z,LAP
```

`KdnNr` ist der einfachheit halber ein einfacher String oder KEY den der Anwender angeben kann.

Dabei enthält `APP_DATE` den Startzeitpunkt des Termins und `SERVICE` den Leistungscode.

Die Zuordnung der CSV-Datei zum jeweiligen IT-Service-Anbieter muss beim Import eindeutig sein.

Der Importprozess muss unbekannte Leistungscodes und ungültige Datensätze erkennen und nachvollziehbar behandeln. Bereits importierte Termine müssen bei der Verfügbarkeitsberechnung berücksichtigt werden.

## Technische Anforderungen

### Backend

- Implementierung in Java
- Ausführbare JAR-Datei
- Ausführung mit einer aktuellen, stabilen Java-Laufzeitumgebung
- REST-API gemäß der Datei `rest-api-spec.yml`
- Erweiterbare Architektur für weitere Anbieter und Dienstleistungen
- Validierung von Buchungen und Kapazitäten

Spring Boot und Spring Data JPA können für die Implementierung verwendet werden.

### Datenbank

Die Daten werden in PostgreSQL oder H2 gespeichert.

Dem Projekt liegt eine `docker-compose.yml`-Datei für eine lokale PostgreSQL-Instanz bei.

Standardkonfiguration:

| Einstellung | Wert |
|---|---|
| Datenbank | `icares` |
| Host | `localhost` |
| Port | `5432` |
| Benutzername | `icares` |
| Passwort | `icares` |

Alternativ kann H2 für eine einfache lokale Ausführung und Tests eingesetzt werden.

### REST-API

Die REST-Schnittstelle wird in der Datei `rest-api-spec.yml` im Swagger-2.0-Format beschrieben.

Die API umfasst folgende Bereiche:

- IT-Service-Anbieter abrufen
- Dienstleistungen eines Anbieters abrufen
- Verfügbare Termine ermitteln
- Termine buchen
- Termine eines Anbieters auflisten
- Termindetails abrufen

Die Spezifikation kann im [Swagger Editor](https://editor.swagger.io/) geöffnet und überprüft werden.

### Frontend

Zusätzlich soll ein einfaches Frontend mit **Ext JS** implementiert werden.

Das Frontend kommuniziert über die REST-API mit dem Backend.

Mindestens folgende Funktionen sollen umgesetzt werden:

- Auswahl eines IT-Service-Anbieters
- Auswahl einer verfügbaren Dienstleistung
- Eingabe eines gewünschten Zeitraums
- Anzeige verfügbarer Terminvorschläge
- Buchung eines Termins
- Anzeige der Termine eines Anbieters in einer Tabelle
- Anzeige der Details eines ausgewählten Termins

Eine einfache Ext-JS-Oberfläche mit Formularen, Dropdowns, Buttons und Grids genügt. Aufwendiges Design und komplexe Benutzerführung sind nicht erforderlich.

## Terminplanung und Kapazitätsregeln

Termine dürfen ausschließlich im 15-Minuten-Takt beginnen.

Beispiele für zulässige Startzeiten:

- 08:00 Uhr
- 08:15 Uhr
- 08:30 Uhr
- 08:45 Uhr

Die Dauer des Termins richtet sich nach der jeweiligen Dienstleistung.

Bei der Verfügbarkeitsprüfung muss die Kapazität über den gesamten Zeitraum eines Termins berücksichtigt werden.

Ein Anbieter mit einer Kapazität von zwei darf beispielsweise zu keinem Zeitpunkt drei gleichzeitig laufende Termine haben.

Die Regeln gelten sowohl für neu gebuchte als auch für importierte Termine.

## Abgrenzung

Nicht Bestandteil der Aufgabe sind:

- Authentifizierung und Autorisierung
- Online-Zahlungen und Rechnungsstellung
- Ersatzteilverwaltung und Warenwirtschaft
- E-Mail- und SMS-Benachrichtigungen
- Ein aufwendiges Frontend-Design

Der Schwerpunkt liegt auf einer robusten Terminplanung, einer nachvollziehbaren REST-API, einem funktionalen Ext-JS-Frontend und einer erweiterbaren Datenmodellierung.

## Projektübergabe

Das fertige Projekt soll enthalten:

- Vollständigen Quellcode für Backend und Frontend
- Ausführbare JAR-Datei des Backends
- Datenbankkonfiguration
- CSV-Import für bestehende Termine
- REST-API-Spezifikation
- README mit Installations- und Startanleitung
- Tests für Terminüberschneidungen, Kapazitätsgrenzen und Buchungen
- Dokumentation wichtiger Architekturentscheidungen

## Bewertungskriterien

Bei der Bewertung werden insbesondere folgende Aspekte berücksichtigt:

- Korrektheit der Termin- und Kapazitätsberechnung
- Vermeidung von Überbuchungen bei parallelen Anfragen
- Qualität und Verständlichkeit des Codes
- Sinnvolle Datenmodellierung
- Erweiterbarkeit für zusätzliche Anbieter und Dienstleistungen
- Fehlerbehandlung beim CSV-Import
- Qualität der REST-API
- Funktionale Integration des Ext-JS-Frontends
- Nachvollziehbare Tests und Dokumentation

Ziel ist eine verständliche, wartbare und erweiterbare Anwendung zur Verwaltung von IT-Service-Anbietern, ihren Dienstleistungen und den zugehörigen Kundenterminen.
Das bedeutete auch, das von der SPezifikation abgewichen werden kann, sollte in diesen Fällen für die Nachvollziehbarkeit gestattet sein.
Der Einsatz von KI ist ebenfalls erlaubt in diesem Fall sollte beschrieben werden wofür diese benutzt wurde (Skills, ADRs, workflows, etc.).
