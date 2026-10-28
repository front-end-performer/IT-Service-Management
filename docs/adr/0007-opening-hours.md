# 0007 – Öffnungszeiten je Anbieter, in Ortszeit

**Status:** angenommen. Ersetzt die Entscheidung „keine Öffnungszeiten“ der ersten Version.

## Kontext

Die Aufgabe definiert keine Öffnungszeiten. Ohne sie bieten Vorschläge Termine um 03:00 oder an Sonntagen an, die ein
IT-Service-Betrieb nicht einhalten kann. Die Anbieter sind deutsch, API und Bestandsdaten dagegen in UTC. Deutschland
wechselt zwischen MEZ (UTC+1) und MESZ (UTC+2); ein festes UTC-Fenster würde sich also zweimal im Jahr um eine Stunde
verschieben.

Die erste Version folgte bewusst wörtlich der Spezifikation und hatte keine Öffnungszeiten. Eine spätere Prüfung anhand
einer Checkliste typischer Fallstricke machte sie zur Anforderung; sie wurden daher als dokumentierte Abweichung
ergänzt.

## Entscheidung

- **Konfiguration je Anbieter:** Jeder Anbieter hat eine `time-zone` (z. B. `Europe/Berlin`) und `opening-hours`, einen
  Zeitraum je Wochentag in lokaler Uhrzeit.

  ```yaml
  time-zone: Europe/Berlin
  opening-hours:
    MONDAY: 08:00-18:00
    SATURDAY: 09:00-13:00
  ```

  - Nicht aufgeführte Tage sind geschlossen.
  - Ein Anbieter ohne `opening-hours` ist durchgehend geöffnet. So bleibt die Konfiguration einfacher oder nicht
    ortsgebundener Anbieter kurz.
  - Ungültige Zeitzonen oder Zeiträume (z. B. `18:00-08:00`) verhindern den Start der Anwendung.
- **Konfigurierte Zeiten:**

  | Anbieter | Mo–Fr | Sa | So |
  |---|---|---|---|
  | IT-Service Müller | 08:00–18:00 | 09:00–13:00 | geschlossen |
  | Computer-Service Schneider | 08:00–17:00 | 10:00–14:00 | geschlossen |

  Alle 40 Bestandstermine liegen innerhalb dieser Zeiten. Der späteste endet um 16:00 Ortszeit.
- **Speicherung:** Die Zeiten werden wie der übrige Katalog gespeichert: `provider.time_zone` plus
  `provider_opening_hours (provider_id, day_of_week, opens_at time, closes_at time)` (Flyway `V2`).
  - Das sind die einzigen Zeitspalten, die keine Zeitpunkte sind, und das ist Absicht. „08:00 in Berlin“ ist eine Regel
    für die Wanduhr, kein Zeitpunkt.
- **Der ganze Termin muss passen:** Er muss frühestens zur Öffnung beginnen und spätestens zur Schließung *desselben
  lokalen Tages* enden.
  - Ein Termin darf genau zur Schließzeit enden (halboffenes Intervall).
  - Er darf **nicht** über die Schließzeit hinaus und **nicht** über Mitternacht laufen, auch wenn der nächste Tag
    geöffnet ist. Eine Reparatur wird nicht über Nacht unterbrochen.
- **Sommerzeit:** Start und Ende jedes Kandidaten werden in die Zeitzone des Anbieters (`ZonedDateTime`) umgerechnet
  und als Ortszeiten verglichen. Öffnung um 08:00 Ortszeit ist daher im Winter 07:00Z und im Sommer 06:00Z, an jedem
  Tag, auch an den Tagen nach der Umstellung.
  - Die nicht existierende Stunde (02:00–03:00 im Frühjahr) und die doppelte Stunde (02:00–03:00 im Herbst) liegen
    außerhalb aller konfigurierten Öffnungszeiten und brauchen keine Sonderbehandlung.
  - Das 15-Minuten-Raster bleibt in UTC. Für Zeitzonen mit ganz- oder halbstündigem Versatz ist es identisch mit
    lokalen Viertelstunden.
- **Überall durchgesetzt:**
  - in `AppointmentScheduler`, dem einzigen Schreibpfad, für Buchungen und Importe;
  - in `SlotFinder`, für Vorschläge.

  Folgen:
  - Eine Buchung außerhalb der Öffnungszeiten wird mit **400** abgelehnt, wie ein Start außerhalb des Rasters oder in
    der Vergangenheit: Die Anfrage kann nie gelingen, unabhängig von der aktuellen Auslastung.
  - Eine Importzeile außerhalb der Öffnungszeiten wird als `OUTSIDE_OPENING_HOURS` abgelehnt, weil die Terminregeln
    auch für importierte Termine gelten.

## Konsequenzen

- Vorschläge sind realistisch, und Anfragen über einen langen Zeitraum erzeugen weit weniger Kandidaten.
- Ein Zeitraum je Tag bedeutet: Eine Mittagspause bräuchte eine Liste von Zeiträumen je Tag. Der Typ `OpeningHours` und
  die Tabelle trügen das mit einer kleinen Änderung.
- Feiertage sind nicht abgebildet. Sie wären eine Liste geschlossener Tage je Anbieter.
- Die API liefert die Zeiten als Ergänzung zum Anbieter (`timeZone`, `openingHours`; ADR 0006). Die Oberfläche zeigt sie
  an und arbeitet in der Ortszeit des Anbieters. Auch Fehlermeldungen nennen sie, z. B. „outside the opening hours of
  IT-MUELLER ({MONDAY=08:00-18:00, …} Europe/Berlin)“.
