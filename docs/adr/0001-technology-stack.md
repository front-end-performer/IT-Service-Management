# 0001 – Technologie-Stack

**Status:** angenommen

## Kontext

Die Aufgabe verlangt ein Java-Backend als ausführbare JAR, PostgreSQL oder H2 und ein Ext-JS-Frontend. Die
mitgelieferte `docker-compose.yml` verwendet `postgres:11-alpine`.

## Entscheidung

- **Backend:** Java 21 (LTS) mit Spring Boot 4.1 (aktuelles stabiles Release; der Initializr bietet 3.x nicht mehr
  an), Spring Data JPA/Hibernate und Flyway für das Schema. `ddl-auto=validate`, Hibernate prüft also nur das Mapping.
- **Build:** Maven, mit eingechecktem Wrapper, sodass kein lokales Maven nötig ist.
- **Datenbank:** Standard ist PostgreSQL. Das Image wurde auf `postgres:17-alpine` angehoben, weil PostgreSQL 11 seit
  November 2023 nicht mehr unterstützt wird (keine Sicherheitsupdates). Datenbankname, Benutzer, Passwort und Port sind
  unverändert, und der Port ist nur an 127.0.0.1 gebunden. Ein `h2`-Profil (PostgreSQL-Kompatibilitätsmodus,
  Dateidatenbank) erlaubt einen Start ohne Einrichtung. Die Migrationen laufen auf beiden.
- **Integrationstests** laufen gegen echtes PostgreSQL über Testcontainers. Das Sperrverhalten, auf das es hier
  ankommt, unterscheidet sich zwischen Datenbanken, deshalb wird H2 für diese Tests nicht verwendet.
- **Frontend:** Ext JS **6.2.0 GPL**, Classic Toolkit, Triton-Theme. Die Laufzeit (`ext-all.js`, Theme, Schriften,
  Lizenz) liegt in `src/main/resources/static/vendor/` und wird von Spring Boot aus derselben JAR ausgeliefert. Es gibt
  kein Sencha Cmd und keinen Node-Build.
  - Die öffentlich herunterladbaren `@sencha/ext`-Pakete 7.x/8.x auf npm stehen unter einer Evaluierungs-/Testlizenz,
    die eine Nutzung über den Testzeitraum hinaus verbietet. 6.2.0 (npm `extjs-gpl`) ist die neueste Ext-JS-Version,
    die unter GPLv3 frei weitergegeben werden darf.
  - Das Mitliefern hält die Anwendung offline lauffähig und auf eine Version festgelegt und vermeidet CORS (gleicher
    Ursprung wie die API).
  - Der Anwendungscode besteht aus wenigen `Ext.define`-Klassen, die per `<script>`-Tag geladen werden. Das genügt für
    die geforderten „Formulare, Dropdowns, Buttons und Grids“.

## Konsequenzen

- `java -jar target/appointments-1.0.0.jar` ist die vollständige Anwendung.
- Rund 8 MB JS/CSS von Drittanbietern liegen im Repository. Ein Update bedeutet, einen Ordner auszutauschen.
- Eine kommerzielle Lizenz für Ext JS 7+ würde moderne Pakete und Builds mit Sencha Cmd ermöglichen. Die Views ließen
  sich weitgehend unverändert übernehmen.
