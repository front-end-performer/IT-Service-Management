# Einsatz von KI

Dieses Projekt wurde in einer Terminalsitzung mit **Claude Code**
(Anthropics agentischer CLI, Modell Claude Opus 5.5) entwickelt, wobei
ein menschlicher Entwickler die Entscheidungen traf und die Ergebnisse
überprüfte.

## Arbeitsablauf

1.  **Planung (Planmodus, nur lesender Zugriff).**

    -   Claude las die Aufgabenstellung, `rest-api-spec.yml`,
        `docker-compose.yml` und die CSV-Dateien und überprüfte die
        lokale Toolchain.
    -   Durch das Zählen der Service-Codes pro Datei stellte Claude
        fest, dass die beiden CSV-Dateien **vertauschte Namen** hatten.
    -   Claude überprüfte die Lizenzsituation von Ext JS in den
        öffentlichen Paketregistern. Dabei stellte sich heraus, dass das
        npm-Paket `@sencha/ext` 8.0 unter einer Testlizenz steht,
        während `extjs-gpl@6.2.0` unter der GPL lizenziert ist.
    -   Offene Fragen wurden dem Entwickler vorgelegt. Dieser entschied
        sich für eine lokal eingebundene Version von Ext JS statt für
        das Laden über ein CDN, zunächst gegen Öffnungszeiten (später
        geändert, siehe Schritt 7) sowie für Maven mit PostgreSQL als
        Standard.
    -   Der daraus entstandene Plan ist unter
        [`ai/implementation-plan.md`](ai/implementation-plan.md)
        archiviert. Vor der Freigabe prüfte der Entwickler ihn anhand
        der Anforderungen der Aufgabe.

2.  **Implementierung in der Reihenfolge des Plans.**

    Die Schritte waren: Grundgerüst (Spring Initializr), der reine
    Planungskern mit Unit-Tests, Persistenz und Katalog, Services mit
    Sperrmechanismen, REST, CSV-Import, Integrations- und
    Nebenläufigkeitstests, Frontend und Dokumentation. Jeder Schritt
    wurde kompiliert und getestet, bevor der nächste begann.

3.  **Überprüfung durch tatsächliche Ausführung statt bloßer
    Behauptungen.**

    -   Die vollständige Testsuite wird mit PostgreSQL in Testcontainers
        ausgeführt.
    -   Das paketierte JAR wurde mithilfe von curl und den echten
        CSV-Dateien einem Smoke-Test unterzogen.
    -   Der Nebenläufigkeitstest wurde auch durch Entfernen der Sperre
        überprüft: Ohne Sperre schlug der Test fehl, weil bei einer
        Kapazität von 2 zehn von zwanzig Buchungen akzeptiert wurden.
    -   Die Ext-JS-Oberfläche wurde ohne sichtbares Browserfenster über
        das Chrome DevTools Protocol bedient (Buchung, Filterung,
        Details, Import); anschließend wurden die Screenshots überprüft.

4.  **Ein durch die Tests aufgedeckter falscher Erwartungswert.**

    Ein Integrationstest erwartete zunächst am 02.11.2026 um 08:00 Uhr
    einen Laptop-Zeitslot. Der Test schlug fehl. Die Analyse zeigte,
    dass nicht der Code, sondern die Erwartung falsch war: Der
    zweistündige Slot hätte sich mit dem Zeitraum 09:15--09:45 Uhr
    überschnitten, in dem die Kapazität bereits ausgeschöpft war. Der
    Test wurde korrigiert und die Begründung in einem Kommentar
    festgehalten.

5.  **Abgleich mit externen Checklisten.**

    Nach der ersten Version stellte der Entwickler zwei Checklisten im
    Stil einer Code-Review mit typischen Problemstellen bereit:
    Nebenläufigkeit, maximale gleichzeitige Belegung im Vergleich zur
    Anzahl überlappender Termine, Entscheidungen beim CSV-Import sowie
    Benennung und Lücken in der Spezifikation. Claude verglich jeden
    Punkt mit dem Code. Die meisten Punkte waren bereits abgedeckt; beim
    Abgleich wurden jedoch drei Lücken gefunden und mit Tests behoben:

    -   Vergangene Zeitslots wurden vorgeschlagen und konnten gebucht
        werden.
    -   Der Buchungsrequest wies `2026-11-02T08:00Z` zurück, obwohl der
        Zeitstempel keine Sekunden enthält.
    -   Es gab keine automatisierte Prüfung, ob die Implementierung mit
        der Swagger-2.0-Spezifikation übereinstimmt. Dafür gibt es jetzt
        `SpecContractTest`.

    Außerdem erhielten die Integrationstests eine fest eingestellte Uhr,
    damit sie nicht nach dem 02.11.2026 ablaufen.

6.  **Vollständigkeit der Tests.**

    Auf Wunsch des Entwicklers wurde jede Anforderung der Aufgabe und
    jeder Checklisten-Stolperstein einem Test zugeordnet
    ([`test-coverage.md`](test-coverage.md)). Fehlende Tests wurden
    ergänzt: Import bei gleichzeitig stattfindenden Buchungen,
    identische parallele Requests, parallele erneute Importe, das
    Peak-Beispiel A/B/C aus der Checkliste, Kapazität 3, Konflikte
    abhängig von der Dateireihenfolge, DIA-Dauer je Anbieter,
    Erweiterbarkeit über die Konfiguration, das H2-Profil und das
    Ausliefern des Frontends. Es wurde bestätigt, dass die
    Import-Race-Tests fehlschlagen, wenn der Import die Sperre des
    jeweiligen Anbieters nicht verwendet.

7.  **Öffnungszeiten.**

    Während der Planung entschied sich der Entwickler zunächst dafür,
    der Spezifikation wortgetreu zu folgen und keine Öffnungszeiten zu
    berücksichtigen. Eine spätere Checkliste des Entwicklers führte
    Öffnungszeiten jedoch als zwingende Anforderung auf. Sie wurden als
    dokumentierte Abweichung ergänzt ([ADR
    0007](adr/0007-opening-hours.md)): pro Anbieter, in lokaler Zeit,
    mit Berücksichtigung der Sommerzeit und ohne Überschreitung der
    Schließzeit oder Mitternacht. Vor der Festlegung der konfigurierten
    Öffnungszeiten prüfte Claude, ob alle bestehenden Termine innerhalb
    dieser Zeiten liegen.

8.  **Gleiches Nebenläufigkeitsverhalten.**

    Bei der Prüfung der Nebenläufigkeits-Checkliste maß Claude, dass H2
    nach etwa zwei Sekunden das Warten auf eine Zeilensperre abbricht,
    während PostgreSQL unbegrenzt wartet. Beide Datenbanken verwenden
    jetzt dasselbe explizit festgelegte Sperr-Timeout; bei einer
    Zeitüberschreitung wird HTTP 503 zurückgegeben. Neue Tests decken
    Folgendes ab:

    -   HTTP 409 für unterlegene Anfragen bei einem Wettlauf um eine
        Buchung;
    -   den tatsächlichen Wartevorgang von Buchungen und Importen auf
        die Sperre;
    -   dass andere Anbieter nicht blockiert werden;
    -   dieselben Prüfungen mit H2.

    Für beide Datenbanken wurde bestätigt, dass jeder Sperrtest
    fehlschlägt, wenn die Sperre entfernt wird.

9.  **Abnahmetestsuite.**

    Der Entwickler stellte einen Testplan mit 52 Szenarien bereit, der
    aus der Aufgabenstellung abgeleitet war (Kapazität, Raster, Endzeit,
    Vorschläge, Buchung, Nebenläufigkeit, Import, Verwaltung, Anbieter
    und Erweiterbarkeit). Der Plan wurde um drei Überlappungsfälle
    erweitert (C11 vollständig innerhalb, C12 umschließend, C13
    unmittelbar davor endend), sodass er 55 Szenarien umfasst. Claude
    setzte ihn im Verhältnis 1:1 als separate Testsuite um; die
    Szenario-IDs stehen in den Methodennamen. Anschließend wurde die
    Suite validiert, indem vier typische Fehler gezielt in den
    Produktionscode eingebaut wurden. Alle vier wurden erkannt (siehe
    [`test-coverage.md`](test-coverage.md)). Der Fehler „neuer Start-
    oder Endzeitpunkt liegt innerhalb eines bestehenden Termins" wird
    ausschließlich durch C12 erkannt.

    -   Ein Szenario (S4) geht davon aus, dass `to` den
        **Startzeitpunkt** eines Vorschlags begrenzt. In diesem Projekt
        wurde entschieden, dass `to` das **Ende** begrenzt. Deshalb wird
        S4 mit einem größeren Zeitbereich ausgeführt; ein Kommentar
        erläutert dies.
    -   X1 fügt den dritten Anbieter über eine gewöhnliche
        Konfigurationsdatei hinzu und nicht über Testcode.
    -   Der Entwickler fragte, was die Aufgabenstellung selbst zu den
        vier Entscheidungen sagt, von denen die Szenarien abhängen.
        Claude zitierte die Aufgabenstellung und die ursprüngliche
        Spezifikation: Alle vier Punkte sind offen gelassen. Die einzige
        Spannung besteht zwischen der wörtlichen Formulierung
        „Eindeutige Kennung" und der Service-ID pro Anbieter. Der
        Entwickler behielt die anbieterspezifische ID bei; die
        Begründung wurde in [ADR
        0002](adr/0002-configuration-driven-catalog.md) dokumentiert.
        Die Zitate stehen in [`test-coverage.md`](test-coverage.md).

10. **Prüfung anhand der Bewertungskriterien.**

    Das paketierte JAR wurde erneut ausgeführt und die im README
    beschriebene manuelle Frontend-Prüfung in einem
    Headless-Chrome-Browser durchgeführt:

    -   alle sieben Funktionen, der Import-Tab und der Wettlauffall
        „Zeitslot wird zwischen Vorschlag und Klick belegt";
    -   die Oberfläche zeigt den Grund für HTTP 409 an und aktualisiert
        die Vorschläge;
    -   keine Fehler in der Browserkonsole.

    Beim ersten Versuch war die Buchung im Wettlauffall erfolgreich.
    Claude stellte fest, dass dies am eigenen Testskript lag: Der Slot
    war mit LAPs „gefüllt" worden, die der Server richtigerweise
    ablehnte. Dadurch blieb der Slot frei. Die Anwendung verhielt sich
    korrekt. Das Skript wurde korrigiert und die Prüfung erneut
    ausgeführt. Außerdem wurde ein Schritt im README entdeckt, der sich
    wie beschrieben nicht reproduzieren ließ; er wurde als Schritt 5b
    neu formuliert.

11. **Korrekturen nach der Prüfung anhand der Bewertungskriterien.**

    Der Entwickler bat darum, drei der gefundenen Schwachstellen zu
    beheben, zwei weitere zu erläutern und anschließend auch eine davon
    zu korrigieren:

    -   **Lokale Zeit:** Die Oberfläche arbeitet jetzt mit der Zeitzone
        des Anbieters. Die API stellt `timeZone` und `openingHours`
        bereit. Die JavaScript-Konvertierung wurde in Node über beide
        Sommerzeitwechsel 2026/27 hinweg überprüft und anschließend in
        Headless Chrome mit absichtlich auf `America/New_York`
        eingestellter Browserzeitzone getestet.
    -   **Chunk-basierter Import:** Der Import wird jetzt in Blöcken
        ausgeführt, sodass Buchungen nicht blockiert werden. Während der
        Tests ergab sich statt der erwarteten Anzahl von 1600 ein Wert
        von 1598. Das war korrektes Verhalten: Die eigene Buchung des
        Tests überschnitt sich mit importierten Zeilen, und zwei Zeilen
        wurden wegen ausgeschöpfter Kapazität abgelehnt. Der Test wurde
        korrigiert; außerdem wurde bestätigt, dass er ohne
        Chunk-Verarbeitung fehlschlägt.
    -   **Seitennavigation:** Die Terminliste ist paginiert und liefert
        `X-Total-Count`.
    -   **`CatalogService`:** Alle Anbieter- und Serviceabfragen laufen
        jetzt über einen zentralen Service. Zuvor war dieselbe Prüfung
        auf „nicht gefunden" an fünf Stellen vorhanden.
    -   Die Ext-JS-Version und ihre Lizenzfolgen wurden erläutert, auf
        Wunsch des Entwicklers jedoch nicht geändert.

12. **Register der Abweichungen.**

    Angeregt durch den Abschnitt „Flexibility" der Aufgabe erstellte
    Claude [`deviations.md`](deviations.md).

    -   **API-Ergänzungen:** Die ursprüngliche und die aktuelle
        `rest-api-spec.yml` wurden eingelesen und maschinell verglichen.
        Dabei wurden ausschließlich Ergänzungen gefunden.
    -   **Verhaltens- und aufgabenbezogene Abweichungen:** Diese wurden
        manuell aufgelistet.
    -   **Jeder Eintrag** enthält den ursprünglichen Zustand, die
        Änderung, die Begründung, die zugehörige ADR und einen Test. Ein
        Skript überprüfte, ob jeder referenzierte Test existiert und ob
        jede YAML-Ergänzung als „Erweiterung" gekennzeichnet ist.

    Bei der Erstellung des Registers wurden drei tatsächliche Probleme
    sichtbar, die alle behoben wurden:

    -   **Falsche Behauptung:** Die Aussage „bestehende Clients
        funktionieren weiterhin" traf bei standardmäßig aktivierter
        Paginierung nicht zu. Auf Wunsch des Entwicklers wurde die
        Paginierung daher optional gemacht.
    -   **Tatsächlicher Fehler:** Eine CSV-Kundennummer mit mehr als 64
        Zeichen ließ den gesamten Import mit HTTP 409 fehlschlagen. Sie
        wird jetzt als fehlerhafte Zeile zurückgewiesen
        (`INVALID_CUSTOMER_NUMBER`); dafür wurde zuerst ein Test
        geschrieben.
    -   **Nicht dokumentiertes Verhalten:** `Location`, `413` und die
        Längenbegrenzung fehlten in der YAML-Datei.

13. **Weiterführende Maßnahmen eines Senior-Entwicklers zu den
    Registerbefunden.**

    Der Entwickler fragte, wie ein Senior-Entwickler die drei Befunde
    gemessen an der Aufgabenstellung beheben würde. Claude trennte die
    unmittelbaren Ursachen von den tieferliegenden Problemen. Der
    Entwickler entschied, zwei Änderungen umzusetzen:

    -   **Importstatus:** `RUNNING`/`COMPLETED`/`FAILED`. Ein
        abgebrochener Import gibt jetzt an, an welcher Stelle er
        gestoppt wurde; der Client erhält HTTP 500 mit einem Link zum
        Bericht. Dies wurde getestet, indem der Scheduler über eine
        Spy-Bean für eine Zeile zum Fehlschlagen gebracht wurde. Ein
        erster Testversuch scheiterte, weil das Stubbing des Spys die
        Transaktionsprüfung `MANDATORY` des Schedulers durchlief. Das
        war ein Problem beim Testaufbau (die Prüfung funktionierte wie
        vorgesehen) und wurde behoben, indem das Stubbing innerhalb
        einer Transaktion ausgeführt wurde.
    -   **Spezifikationsprüfung auf Antwortebene:** Jede Antwort jedes
        API-Tests wird gegen die YAML-Datei geprüft. Dies wurde
        verifiziert, indem nacheinander drei Spezifikationseinträge
        gelöscht wurden; jeder einzelne Fehler wurde erkannt.

14. **Abschließende Prüfung anhand der Aufgabenstellung.**

    Claude ging die Aufgabenstellung Abschnitt für Abschnitt durch und
    überprüfte das Projekt erneut:

    -   das paketierte JAR mit PostgreSQL 17 und den Migrationen V1--V3;
    -   beide Legacy-Dateien: jeweils 20 von 20 Zeilen mit Status
        `COMPLETED`;
    -   20 parallele Buchungen: 2 × 201 und 18 × 409;
    -   die vollständige Frontend-Prüfung ohne Browserfehler;
    -   die erweiterte Spezifikation, die neben der ursprünglichen Datei
        als Swagger 2.0 validiert wurde.

    Die folgenden Befunde wurden auf Wunsch des Entwicklers alle
    behoben:

    -   **Servicebezeichnung:** „Betriebssystem-Installation" entsprach
        nicht der Bezeichnung „Betriebssysteminstallation" aus der
        Aufgabenstellung.
    -   **Fehlendes Lieferartefakt:** Das ausführbare JAR war nicht
        mitgeliefert worden. Ein GitHub-Actions-Workflow erstellt und
        testet es nun, hängt es als Artefakt an und veröffentlicht bei
        Versions-Tags ein Release. Der Workflow wurde mit actionlint
        geprüft.
    -   **UTC in der Oberfläche:** In Servermeldungen enthaltene
        UTC-Zeitstempel wurden in der Oberfläche angezeigt. Sie werden
        jetzt in der Zeitzone des Anbieters dargestellt.

15. **Überprüfung nach YAGNI / KISS / DRY.**

    Claude las die gesamte Anwendung und prüfte sie anhand der drei
    Prinzipien. Jeden Verdacht bestätigte es durch eine Suche (zum
    Beispiel durch den Nachweis, dass es keine Verwendungen gab), statt
    sich allein auf eine Sichtprüfung zu verlassen. Der Entwickler
    übernahm drei Gruppen von Änderungen:

    -   **YAGNI:** Vier ungenutzte Test-Starter, die undokumentierte
        H2-Konsole, eine ungenutzte Scaffold-Klasse und zwei ungenutzte
        Getter wurden entfernt.
    -   **KISS:** Ein selbst geschriebener `FilterReader` zum
        Überspringen einer BOM wurde durch einfache String-Verarbeitung
        der größenbegrenzten Upload-Datei ersetzt. Der Import wird jetzt
        in einer Transaktion abgeschlossen. Die Öffnungszeiten werden
        einmal pro Anbieter aufgebaut.
    -   **DRY:** Vier Kopien des Testcodes zum gleichzeitigen Starten
        von N Requests wurden in einen gemeinsamen Helfer
        (`Simultaneously`) überführt; die URL des Importberichts wird
        nur noch an einer Stelle erstellt.

    Bewusst beibehaltene, gerechtfertigte Komplexität: der zentrale
    Schreibpfad, `CatalogService`, der blockweise Import mit Status
    sowie die beiden Zuordnungen von Fehlermeldungen (jeweils zwei
    Verbraucher, also noch unterhalb der „Rule of Three").

    Bei diesem Schritt passierte ein Fehler, der korrigiert wurde: Ein
    Commit wurde erstellt, obwohl die Kompilierung fehlgeschlagen war
    (beim Entfernen der Getter war eine geschweifte Klammer verloren
    gegangen). Der Fehler wurde sofort gefunden, behoben und der Commit
    angepasst, bevor etwas gepusht wurde. Danach erfolgten Commits nur
    noch nach erfolgreichem Build. Nach der Refaktorierung des Helfers
    wurden die Race-Tests erneut geprüft: Alle fünf schlagen ohne Sperre
    fehl.

16. **Überarbeitung der Oberfläche anhand eines Designentwurfs.**

    Der Entwickler legte einen Designentwurf (vier Ansichten) vor.
    Claude glich ihn mit Aufgabe, API und bestehender Oberfläche ab und
    fand Stellen, die die API nicht liefert oder anders meint: „volle“
    Startzeiten und „1 von 2 frei“ (die Vorschläge enthalten nur
    buchbare Startzeiten), nur ein Tag statt bis zu 31, ein einziger
    Konflikttext für zwei verschiedene 409-Fälle, Duplikate als Fehler
    statt als übersprungen, eine fest verdrahtete Zeitzone. Der
    Entwickler entschied, die API unverändert zu lassen und nur
    buchbare Zeiten anzuzeigen; der Entwurf wurde entsprechend
    angepasst.

    Umgesetzt mit Ext-JS-Bordmitteln (Dataview mit XTemplate, Grid,
    Panels) und einer kleinen eigenen CSS-Datei ohne externe
    Ressourcen: Startzeiten als Kacheln nach Tag und Stunde, Meldungen
    in der Seite je nach Statuscode (409 belegt, 409 Duplikat, 503,
    sonstige), Zeitspalte und Details in der Zeitzone des Anbieters mit
    UTC und Dauer, Importzähler für importiert, übersprungen und
    abgelehnt. Die beiden 409-Fälle unterscheidet die Oberfläche am
    Text der Servermeldung, da Status und Titel gleich sind.

    Geprüft per Headless-Chromium gegen die laufende Anwendung (Profil
    `h2`), auch mit auf America/New_York gestelltem Browser: Buchung,
    Duplikat, „gerade belegt“ (Platz vorher über die API gefüllt),
    Import und Details, ohne JavaScript-Fehler. Zwei Fehlschläge dabei
    lagen am Testskript (gleiche Kundennummer, durch frühere Läufe
    bereits volle Zeiten), nicht an der Anwendung.

## Was der Mensch beigetragen hat

-   Legte Umfang und Rahmenbedingungen fest: Einbindung von Ext JS,
    Öffnungszeiten (zunächst keine, später erforderlich), Build-Tool und
    Datenbank.
-   Genehmigte den Plan und verlangte vor der Implementierung einen
    Abgleich mit der Aufgabenstellung.
-   Installierte das JDK, als sich herausstellte, dass auf dem Rechner
    nur eine JRE vorhanden war. Der Assistent ist so konfiguriert, dass
    er niemals Root-Rechte verwendet.

## Was kritisch überprüft werden sollte

Der gesamte Code wurde von der KI generiert und wie oben beschrieben
durch Tests und tatsächliche Ausführung überprüft. Die
Designentscheidungen sind in den ADRs erläutert ([ADRs](adr/README.md)),
damit sie anhand ihrer jeweiligen Vorzüge beurteilt werden können.
Folgende Bereiche sollten besonders sorgfältig geprüft werden:

-   Die Sperrstrategie (siehe [ADR
    0003](adr/0003-pessimistic-provider-lock.md))
-   Die Importregeln für bereits überbuchte Legacy-Zeilen (siehe [ADR
    0005](adr/0005-csv-import-policy.md))
-   Die Umrechnung der Oberfläche in die Zeitzone des Anbieters
    (siehe [ADR 0004](adr/0004-time-model.md))
