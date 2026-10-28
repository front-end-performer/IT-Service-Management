# 0001 – Technology stack

**Status:** accepted

## Context

The task requires a Java backend packaged as an executable JAR, PostgreSQL or H2, and an Ext JS frontend. The provided
`docker-compose.yml` uses `postgres:11-alpine`.

## Decision

- **Backend:** Java 21 (LTS) with Spring Boot 4.1 (the current stable release; Initializr no longer offers 3.x),
  Spring Data JPA/Hibernate, and Flyway for the schema. `ddl-auto=validate`, so Hibernate only checks the mapping.
- **Build:** Maven, with the wrapper committed, so no local Maven is needed.
- **Database:** PostgreSQL is the default. The image is bumped to `postgres:17-alpine`, because PostgreSQL 11 has been
  end-of-life since November 2023 (no more security fixes). Database name, user, password and port are unchanged,
  and the port is bound to 127.0.0.1 only. An `h2` profile (PostgreSQL compatibility mode, file database) allows a
  zero-setup start. The migrations are written to run on both.
- **Integration tests** run against real PostgreSQL via Testcontainers. Locking behaviour, which matters here,
  differs between databases, so H2 is not used for those tests.
- **Frontend:** Ext JS **6.2.0 GPL**, classic toolkit, Triton theme. The runtime (`ext-all.js`, theme, fonts,
  license) is vendored into `src/main/resources/static/vendor/` and served by Spring Boot from the same JAR. There is
  no Sencha Cmd and no Node build.
  - The publicly downloadable `@sencha/ext` 7.x/8.x packages on npm carry an evaluation/trial license that forbids
    use beyond the trial. 6.2.0 (npm `extjs-gpl`) is the newest Ext JS build that can be redistributed freely under
    GPLv3.
  - Vendoring keeps the app working offline and pinned to one version, and avoids CORS (same origin as the API).
  - The app code is a handful of `Ext.define` classes loaded by `<script>` tags. This is enough for "forms,
    dropdowns, buttons and grids" as required.

## Consequences

- `java -jar target/appointments-1.0.0.jar` is the complete application.
- About 8 MB of third-party JS/CSS sits in the repository. Upgrading means replacing one folder.
- A commercial Ext JS 7+ license would allow modern packages and Sencha Cmd builds. The views would carry over largely
  unchanged.
