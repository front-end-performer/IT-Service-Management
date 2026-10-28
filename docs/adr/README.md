# Architecture Decision Records

Each record follows the format *Context, Decision, Consequences*.

| # | Decision |
|---|---|
| [0001](0001-technology-stack.md) | Technology stack, PostgreSQL 17, Ext JS 6.2 GPL vendored |
| [0002](0002-configuration-driven-catalog.md) | Providers and services come from configuration |
| [0003](0003-pessimistic-provider-lock.md) | Overbooking is prevented by a pessimistic lock per provider |
| [0004](0004-time-model.md) | UTC instants, half-open intervals, peak-concurrency check |
| [0005](0005-csv-import-policy.md) | CSV import: explicit provider, row-level validation, traceable report |
| [0006](0006-api-deviations.md) | Deviations from and extensions to `rest-api-spec.yml` |
