# 0002 – Providers and services come from configuration

**Status:** accepted

## Context

Two providers are needed now, and more must be possible "without fundamental changes to the source code". The same
service code can have different durations at different providers (DIA: 30 min at Müller, 15 min at Schneider).

## Decision

- **Data model:**
  - `provider (id, name, capacity)`
  - `service_offering (id, provider_id, code, name, duration_minutes)` with `UNIQUE(provider_id, code)`. A service
    belongs to exactly one provider, so durations can differ per provider and there is no shared global service
    table.
  - `appointment` references both, and stores the computed `end_at` (see 0004).
- **Source of truth:** `catalog.providers` in `application.yml`, bound to a validated `@ConfigurationProperties`
  record. `CatalogInitializer` upserts it into the database on every start: it inserts new entries and updates names,
  capacities and durations. Entries removed from the configuration are **kept**, because appointments may reference
  them.
- **Internal names:** the domain uses `Provider` and `ServiceOffering` (table `service_offering`). The spec names
  `it-service`/`serviceId` and `repairService`/`repairServiceId` appear only in controllers and DTOs (`api/`).
- **Scope of `repairServiceId`:** the provider id is a readable key (`IT-MUELLER`, `CS-SCHNEIDER`), matching the spec
  example. `repairServiceId` is the service code, so it is **unique per provider only**, exactly as in the spec
  example (`id: DIA`, `code: DIA`).
  - Every endpoint has the provider in its path, so a service is always looked up by `(providerId, code)` and never by
    code alone.
  - The CSV `SERVICE` column resolves the same way: the provider comes from the import request.
  - The internal surrogate key (`service_offering.id`, a number) is not exposed.
  - *Rejected alternative:* globally unique ids such as `MUELLER-DIA`. They would read more literally as a "unique
    identifier", but they deviate from the spec example and duplicate information already in the path.

## Consequences

- Adding a provider or service means a config change and a restart. No code changes and no migration are needed.
- **Changing a duration** affects only future bookings. Existing appointments keep their stored end time, which is
  the safe choice for already-agreed appointments.
- No write API for the catalog. The task requires management of providers and services, but not via REST, and the
  spec has no such endpoints. An admin API could later write to the same tables.
