# 0002 – Anbieter und Dienstleistungen kommen aus der Konfiguration

**Status:** angenommen

## Kontext

Jetzt werden zwei Anbieter gebraucht, weitere müssen „ohne grundlegende Änderungen am Quellcode“ möglich sein.
Derselbe Leistungscode kann bei verschiedenen Anbietern unterschiedlich lange dauern (DIA: 30 min bei Müller, 15 min
bei Schneider).

## Entscheidung

- **Datenmodell:**
  - `provider (id, name, capacity)`
  - `service_offering (id, provider_id, code, name, duration_minutes)` mit `UNIQUE(provider_id, code)`. Eine
    Dienstleistung gehört genau einem Anbieter, deshalb können Dauern je Anbieter abweichen, und es gibt keine
    gemeinsame globale Dienstleistungstabelle.
  - `appointment` verweist auf beide und speichert das berechnete `end_at` (siehe 0004).
- **Maßgebliche Quelle:** `catalog.providers` in `application.yml`, gebunden an einen validierten
  `@ConfigurationProperties`-Record. `CatalogInitializer` gleicht ihn bei jedem Start mit der Datenbank ab: neue
  Einträge werden eingefügt, Namen, Kapazitäten und Dauern aktualisiert. Aus der Konfiguration entfernte Einträge
  **bleiben erhalten**, weil Termine auf sie verweisen können.
- **Ein Einstiegspunkt:** `CatalogService` ist der einzige Weg, auf dem der Rest der Anwendung Anbieter und
  Dienstleistungen liest. Ihn nutzen REST-Schicht, Buchung, Vorschläge, Abfragen und Import.
  - Er besitzt die „nicht gefunden“-Regeln, sodass ein unbekannter Anbieter oder Dienst überall gleich beantwortet
    wird.
  - Er besitzt auch den gesperrten Zugriff (`lockProvider`, `SELECT … FOR UPDATE`), mit dem jede Änderung am
    Terminplan beginnt.
- **Interne Namen:** Die Domäne verwendet `Provider` und `ServiceOffering` (Tabelle `service_offering`). Die Namen der
  Spezifikation, `it-service`/`serviceId` und `repairService`/`repairServiceId`, kommen nur in Controllern und DTOs
  (`api/`) vor.
- **Öffentliche Kennungen:** Die Anbieter-ID ist ein lesbarer Schlüssel (`IT-MUELLER`, `CS-SCHNEIDER`), passend zum
  Beispiel der Spezifikation. `repairServiceId` ist der Leistungscode; warum, erklärt der nächste Abschnitt.

### Warum `repairServiceId` nur je Anbieter eindeutig ist

**Was Aufgabe und Spezifikation sagen.** Die Aufgabe nennt für jede Dienstleistung *"Eindeutige Kennung"* **und**
*"Leistungscode"* als zwei getrennte Felder (Anforderung 2). Außerdem heißt es *"Jede Dienstleistung besitzt einen
eindeutigen Leistungscode"* und *"Auch bei identischen Leistungscodes können unterschiedliche Bearbeitungszeiten
gelten."* Das Beispiel der Spezifikation für `repairService` ist `id: DIA`, `code: DIA`.

Das zieht in zwei Richtungen:
- Wörtlich gelesen legen zwei getrennte Felder eine Kennung nahe, die über alle Anbieter hinweg eindeutig ist.
- Das Beispiel der Spezifikation setzt die Kennung gleich dem Code. DIA gibt es bei beiden Anbietern (30 bzw. 15
  Minuten), also kann der Code und damit die ID nur *je Anbieter* eindeutig sein. Auch der „eindeutige Leistungscode“
  der Aufgabe selbst stimmt nur je Anbieter.

**Unsere Lesart.** Eine Dienstleistung ist ein Angebot *eines Anbieters*. Ihre Identität ist das Paar (Anbieter, Code).
- In der API ist dieses Paar immer vollständig, weil jeder Endpunkt mit `repairServiceId` den Anbieter auch im Pfad
  trägt (`/it-services/{serviceId}/…`).
- `DIA` unter `/it-services/IT-MUELLER` bezeichnet daher genau eine Dienstleistung: Müllers 30-minütige Diagnose.
- Die Kennung ist in jedem Kontext eindeutig, in dem sie verwendet werden kann. Genau das verlangt „eindeutig“ von
  einer Kennung.

**Warum das besser ist als eine global eindeutige öffentliche ID:**
1. **Konformität mit der Spezifikation.** Es entspricht genau dem Beispiel der Spezifikation, und die Spezifikation ist
   der API-Vertrag, auf den sich Clients verlassen.
2. **Ein Schlüssel überall.** Es ist derselbe Schlüssel, den die Bestands-CSV verwendet (`SERVICE`). Importe,
   Buchungen und Vorschläge lösen eine Dienstleistung identisch auf: `(Anbieter aus dem Pfad, Code)`.
3. **Stabil.** Er ist in jeder Umgebung und nach jedem Neustart gleich. Ein generierter Schlüssel würde je Datenbank
   abweichen; Clients und Tests könnten ihn nicht fest verwenden.
4. **Lesbar.** `DIA` ist in URLs, Logs, Fehlermeldungen und im Frontend aussagekräftig.

**Die globale Kennung gibt es, intern.** `service_offering.id` (ein numerischer Primärschlüssel) ist global eindeutig
und wird für alle Fremdschlüssel verwendet. Er wird bewusst nicht veröffentlicht, weil kein Client eine
Dienstleistung ansprechen muss, ohne ihren Anbieter zu kennen.

**Sicherheitsnetz.** Eine Dienstleistung wird nie allein über den Code gesucht. Die einzige Abfrage ist
`ServiceOfferingRepository.findByProviderIdAndCode`. Fragt man Müller nach Schneiders `VIR`, kommt 404; das prüfen die
Abnahmeszenarien B5 und S9.

**Verworfene Alternative:** eine globale öffentliche ID wie `IT-MUELLER-DIA`.
- Sie weicht vom Beispiel der Spezifikation ab.
- Sie wiederholt den Anbieter, der schon im Pfad steht.
- Die API müsste zwei Formen akzeptieren (ID und Code) oder den Bestandsschlüssel aufgeben.

Braucht ein künftiger Client eine globale ID, lässt sie sich abwärtskompatibel ergänzen: ein zusätzliches Feld
veröffentlichen und den Code weiter akzeptieren.

## Konsequenzen

- Einen Anbieter oder eine Dienstleistung hinzuzufügen bedeutet eine Konfigurationsänderung und einen Neustart. Weder
  Codeänderungen noch Migrationen sind nötig.
- **Eine geänderte Dauer** wirkt sich nur auf künftige Buchungen aus. Bestehende Termine behalten ihre gespeicherte
  Endzeit, die sichere Wahl für bereits vereinbarte Termine.
- **Ein entfernter Anbieter oder eine entfernte Dienstleistung bleibt buchbar.** Die Zeile bleibt in der Datenbank,
  weil Termine auf sie verweisen, und es gibt kein Merkmal „inaktiv“. Nach dem Neustart wird sie also weiterhin
  aufgelistet, vorgeschlagen, gebucht und importiert. Wer einen Eintrag wirklich stilllegen will, braucht eine Spalte
  `active` (Flyway-Migration), die der Abgleich für fehlende Einträge auf `false` setzt und die `CatalogService` beim
  Auflisten und Buchen beachtet. Bestehende Termine blieben lesbar. Bewusst nicht umgesetzt, siehe
  [Register E1](../deviations.md#e--bekannte-grenzen).
- **Eine gesenkte Kapazität oder verkürzte Öffnungszeiten** gelten nur für neue Buchungen und Importe. Bestehende
  Termine werden nicht erneut geprüft. Ein Zeitraum kann danach mehr gleichzeitige Termine haben als die neue
  Kapazität oder außerhalb der neuen Öffnungszeiten liegen; er nimmt aber keine weiteren an. Wer die Kapazität senkt,
  klärt betroffene Termine selbst. Siehe [Register E2](../deviations.md#e--bekannte-grenzen).
- Keine schreibende API für den Katalog. Die Aufgabe verlangt die Verwaltung von Anbietern und Dienstleistungen, aber
  nicht über REST, und die Spezifikation hat keine solchen Endpunkte. Eine Admin-API könnte später in dieselben
  Tabellen schreiben.
