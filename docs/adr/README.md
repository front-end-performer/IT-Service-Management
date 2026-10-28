# Architecture Decision Records

Jeder Eintrag folgt dem Schema *Kontext, Entscheidung, Konsequenzen*.

| # | Entscheidung |
|---|---|
| [0001](0001-technology-stack.md) | Technologie-Stack, PostgreSQL 17, Ext JS 6.2 GPL mitgeliefert |
| [0002](0002-configuration-driven-catalog.md) | Anbieter und Dienstleistungen kommen aus der Konfiguration |
| [0003](0003-pessimistic-provider-lock.md) | Überbuchung wird durch eine pessimistische Sperre je Anbieter verhindert |
| [0004](0004-time-model.md) | UTC-Instants, halboffene Intervalle, Prüfung der Spitzenlast |
| [0005](0005-csv-import-policy.md) | CSV-Import: ausdrücklicher Anbieter, Prüfung je Zeile, nachvollziehbarer Bericht |
| [0006](0006-api-deviations.md) | Abweichungen von und Erweiterungen zu `rest-api-spec.yml` |
| [0007](0007-opening-hours.md) | Öffnungszeiten je Anbieter in Ortszeit, Sommerzeit, kein Überschreiten von Schließzeit oder Mitternacht |
