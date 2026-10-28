# 0002 – Providers and services come from configuration

**Status:** accepted

## Context

Two providers are needed now, and more must be possible "without fundamental changes to the source code". The same
service code can have different durations at different providers (DIA: 30 min at Müller, 15 min at Schneider).

## Decision

- **Data model:**
  - `provider (id, name, capacity)`
  - `repair_service (id, provider_id, code, name, duration_minutes)` with `UNIQUE(provider_id, code)`. A service
    belongs to exactly one provider, so durations can differ per provider and there is no shared global service
    table.
  - `appointment` references both, and stores the computed `end_at` (see 0004).
- **Source of truth:** `catalog.providers` in `application.yml`, bound to a validated `@ConfigurationProperties`
  record. `CatalogInitializer` upserts it into the database on every start: it inserts new entries and updates names,
  capacities and durations. Entries removed from the configuration are **kept**, because appointments may reference
  them.
- **Public identifiers:** the provider id is a readable key (`IT-MUELLER`, `CS-SCHNEIDER`), matching the spec example.
  The service's public id is its code, which is unique per provider. It is the same key the legacy CSV uses, so
  `repairServiceId` from the API and `SERVICE` from the CSV resolve the same way.

## Consequences

- Adding a provider or service means a config change and a restart. No code changes and no migration are needed.
- **Changing a duration** affects only future bookings. Existing appointments keep their stored end time, which is
  the safe choice for already-agreed appointments.
- No write API for the catalog. The task requires management of providers and services, but not via REST, and the
  spec has no such endpoints. An admin API could later write to the same tables.
