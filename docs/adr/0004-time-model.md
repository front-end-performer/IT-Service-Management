# 0004 – UTC-Instants, halboffene Intervalle, Prüfung der Spitzenlast

**Status:** angenommen

## Kontext

Die CSV-Daten enthalten einen UTC-Offset (`2026-11-02T08:00Z`), und die API verwendet ISO-8601-`date-time`. Die
Kapazität muss „während der gesamten Dienstleistungsdauer“ reichen; nur den Startzeitpunkt zu prüfen genügt also nicht.

## Entscheidung

- **Zeitbehandlung:** Alle Zeiten sind `java.time.Instant`, gespeichert als `timestamp with time zone`, mit der
  JDBC-Zeitzone UTC. Eingaben dürfen einen beliebigen Offset haben, Ausgaben sind immer `Z`. Ein CSV-Datum ohne Offset
  wird **abgelehnt** (`INVALID_DATE`), statt stillschweigend interpretiert zu werden.
- **Raster:** Das 15-Minuten-Raster ist in UTC definiert: Ein gültiger Start hat `epochSecond % 900 == 0` und keine
  Nanosekunden. Für alle Zeitzonen mit ganz- oder halbstündigem Versatz, einschließlich Europe/Berlin, ist das
  identisch mit lokalen Viertelstunden.
- **Intervalle** sind halboffen, `[Start, Ende)`. Unmittelbar aufeinanderfolgende Termine überschneiden sich nie.
- **Endzeit:** `Ende = Start + Dauer` berechnet der Server und **speichert** sie. Überschneidungsabfragen werden so zu
  `start < :to AND end > :from` über einen Index `(provider_id, start_at, end_at)`, und eine geänderte Dauer in der
  Konfiguration verschiebt keine bestehenden Termine.
- **Kapazitätsprüfung, Spitzenlast:** Ein Sweep-Line-Verfahren läuft über die Start- und Endereignisse der Termine, die
  den Kandidaten überlappen; bei gleichem Zeitpunkt kommen Enden vor Starts. Es liefert die **höchste** parallele Last
  innerhalb des Kandidaten, und der Kandidat passt, wenn `Spitze < Kapazität`. So werden Lasten erkannt, die sich erst
  nach dem Start des Kandidaten aufbauen, und Termine, die jeweils den Kandidaten, aber nicht einander überlappen,
  werden nicht doppelt gezählt.
  - Das ist gleichwertig damit, jeden 15-Minuten-Slot zu prüfen, den der Kandidat abdeckt: Die Last ändert sich nur an
    Termingrenzen, und genau diese Ereignisse besucht das Verfahren.
  - Anders als eine Slot-Tabelle bleibt es für jede Dauer exakt (siehe unten).
- **Vergangenheit:** Neue Buchungen dürfen nicht in der Vergangenheit beginnen (`400`), und Vorschläge beginnen bei
  `max(from, jetzt)`. Importierte Bestandstermine dürfen in der Vergangenheit liegen, denn sie sind Historie. „Jetzt“
  kommt aus einer injizierten `Clock`; Tests setzen sie fest auf 2026-10-28T12:00Z, damit sie nicht brechen, wenn die
  Beispieldaten in der Vergangenheit liegen.
- **Dauern, die keine Vielfachen von 15 Minuten sind,** werden ohne Rundung unterstützt. Nur *Startzeiten* sind an das
  Raster gebunden. Die Kapazitätsprüfung arbeitet mit exakten Intervallen, nicht mit 15-Minuten-Fächern; eine künftige
  20-minütige Dienstleistung (08:00–08:20) blockiert also korrekt einen Start um 08:15 und gibt 08:30 frei. Das prüfen
  `SlotFinderTest` und `CatalogExtensibilityTest`.
  - Das Ergebnis ist dasselbe wie beim „Aufrunden der belegten Slots“. Auch jeder andere Termin beginnt im Raster,
    also kann kein Rasterstart zwischen 08:20 und 08:30 liegen.
  - Eine Prüfung schon bei der Konfiguration ist daher unnötig, und die gespeicherte Endzeit bleibt die echte.
- **Vorschläge:** Die Suche rundet `from` auf das Raster auf, geht in 15-Minuten-Schritten vor, behält Starts, deren
  Ende ≤ `to` ist, und lädt die überlappenden Termine einmal je Suche. Der Zeitraum ist auf 31 Tage begrenzt.
  - *Die Aufgabe sagt:* Die Spezifikation definiert `to` nur als *"Ende des Suchzeitraums"*, und die Aufgabe nennt die
    *"Eingabe eines gewünschten Zeitraums"*. Ob `to` den Start oder das Ende begrenzt, bleibt offen.
  - Wir lesen „Ende des Suchzeitraums“ so: Der ganze Termin liegt innerhalb des Zeitraums.
- **Reiner Kern:** Das Paket `scheduling` hat keine Abhängigkeiten zu Spring oder JPA und wird isoliert per Unit-Test
  geprüft.

## Konsequenzen

- **Zeitzonen:** API und CSV bleiben in UTC. Die Oberfläche zeigt und erwartet Zeiten in der Zeitzone des
  **Anbieters**, die die API je Anbieter liefert (`timeZone`). Sie verwendet nicht die Zeitzone des Browsers, weil auch
  ein Kunde im Ausland in der Ortszeit des Betriebs bucht.
  - Die Umrechnung nutzt die `Intl`-Zeitzonendaten des Browsers, einschließlich der Umstellungstage. Seit der Umstellung
    auf MEZ am 2026-10-25 wird `2026-11-02T08:00Z` als 09:00 angezeigt.
- **Öffnungszeiten** gelten zusätzlich zu diesen Regeln, je Anbieter, in Ortszeit und mit Sommerzeit. Siehe
  [ADR 0007](0007-opening-hours.md).
