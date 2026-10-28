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
- **One entry point:** `CatalogService` is the only way the rest of the application reads providers and services.
  It is used by the REST layer, booking, suggestions, queries and import.
  - It owns the "not found" rules, so an unknown provider or service is answered identically everywhere.
  - It also owns the locked access (`lockProvider`, `SELECT … FOR UPDATE`) that every schedule change starts with.
- **Internal names:** the domain uses `Provider` and `ServiceOffering` (table `service_offering`). The spec names
  `it-service`/`serviceId` and `repairService`/`repairServiceId` appear only in controllers and DTOs (`api/`).
- **Public identifiers:** the provider id is a readable key (`IT-MUELLER`, `CS-SCHNEIDER`), matching the spec
  example. `repairServiceId` is the service code; see the next section for why.

### Why `repairServiceId` is unique per provider

**What the task and spec say.** The task lists, for every service, *"Eindeutige Kennung"* **and** *"Leistungscode"*
as two separate fields (req. 2). It also says *"Jede Dienstleistung besitzt einen eindeutigen Leistungscode"* and
*"Auch bei identischen Leistungscodes können unterschiedliche Bearbeitungszeiten gelten."* The spec's example for
`repairService` is `id: DIA`, `code: DIA`.

These pull in two directions:
- Read literally, two separate fields suggest an identifier that is unique across all providers.
- The spec example makes the identifier equal to the code. DIA exists at both providers (30 vs. 15 minutes), so the
  code, and therefore the id, can only be unique *per provider*. The task's own "eindeutiger Leistungscode" is
  likewise only true per provider.

**Our reading.** A service is an offering *of one provider*. Its identity is the pair (provider, code).
- In the API this pair is always complete, because every endpoint that takes a `repairServiceId` also carries the
  provider in its path (`/it-services/{serviceId}/…`).
- `DIA` under `/it-services/IT-MUELLER` therefore identifies exactly one service: Müller's 30-minute diagnosis.
- The identifier is unique in every context where it can be used, which is what "eindeutig" requires of an
  identifier.

**Why this beats a globally unique public id:**
1. **Spec compliance.** It matches the spec example exactly, and the spec is the API contract clients rely on.
2. **One key everywhere.** It is the same key the legacy CSV uses (`SERVICE`). Imports, bookings and suggestions
   resolve a service identically: `(provider from path, code)`.
3. **Stable.** It is the same in every environment and after every restart. A generated key would differ per
   database, so clients and tests could not hard-code it.
4. **Readable.** `DIA` is meaningful in URLs, logs, error messages and the frontend.

**The global identifier exists, internally.** `service_offering.id` (a numeric primary key) is globally unique and
used for all foreign keys. It is deliberately not exposed, because no client needs to address a service without
knowing its provider.

**Safety net.** A service is never looked up by code alone. The only lookup is
`ServiceOfferingRepository.findByProviderIdAndCode`. Asking Müller for Schneider's `VIR` returns 404, which is
tested by the acceptance scenarios B5 and S9.

**Rejected alternative:** a global public id such as `IT-MUELLER-DIA`.
- It deviates from the spec example.
- It repeats the provider that is already in the path.
- The API would have to accept two forms (id and code) or break the legacy key.

Should a future client need a global id, adding it is backwards-compatible: expose an extra field and keep accepting
the code.

## Consequences

- Adding a provider or service means a config change and a restart. No code changes and no migration are needed.
- **Changing a duration** affects only future bookings. Existing appointments keep their stored end time, which is
  the safe choice for already-agreed appointments.
- No write API for the catalog. The task requires management of providers and services, but not via REST, and the
  spec has no such endpoints. An admin API could later write to the same tables.
