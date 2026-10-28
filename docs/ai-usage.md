# Use of AI

This project was built with **Claude Code** (Anthropic's agentic CLI, model Claude Opus 5.5) in a terminal session,
with a human developer making the decisions and reviewing the results.

## Workflow

1. **Planning (plan mode, read-only).**
   - Claude read the task, `rest-api-spec.yml`, `docker-compose.yml` and the CSV files, and checked the local
     toolchain.
   - It found that the two CSV files have **swapped names**, by counting service codes per file.
   - It checked the Ext JS licensing situation on the public registries. The npm `@sencha/ext` 8.0 package turned out
     to be trial-licensed, while `extjs-gpl@6.2.0` is GPL.
   - Open questions were put to the developer, who decided: Ext JS vendored rather than loaded from a CDN, no opening
     hours (later revised, see step 7), Maven with PostgreSQL as the default.
   - The resulting plan is archived in [`ai/implementation-plan.md`](ai/implementation-plan.md). The developer
     checked it against the task's requirements before approving.
2. **Implementation, in the plan's order.** The steps were: scaffolding (Spring Initializr), the pure scheduling core
   with unit tests, persistence and catalog, services with locking, REST, CSV import, integration and concurrency
   tests, frontend, and docs. Each step was compiled and tested before the next one.
3. **Verification by execution, not assertion.**
   - The full test suite runs against PostgreSQL in Testcontainers.
   - The packaged JAR was smoke-tested with curl, using the real CSV files.
   - The concurrency test was itself checked by removing the lock: it then failed with 10 of 20 bookings accepted at
     capacity 2.
   - The Ext JS UI was driven headlessly through the Chrome DevTools Protocol (booking, filtering, details, import),
     and the screenshots were inspected.
4. **One wrong expectation found by the tests.** An integration test initially expected a laptop slot at 08:00 on
   2026-11-02. The test failed, and analysis showed the expectation was wrong, not the code: the 2-hour slot would
   overlap 09:15–09:45, where capacity is already reached. The test was corrected and the reasoning recorded in a
   comment.

5. **Review against external checklists.** After the first version, the developer provided two reviewer-style
   checklists of common pitfalls: concurrency, peak concurrency versus overlap count, CSV import decisions, spec
   naming and gaps. Claude compared every point with the code. Most were already covered; the comparison found three
   gaps, which were fixed with tests:
   - past slots were suggested and bookable;
   - the booking body rejected `2026-11-02T08:00Z`, which has no seconds;
   - there was no automated check that the implementation matches the Swagger 2.0 spec (now `SpecContractTest`).

   The integration tests also got a fixed clock, so they don't expire after 2026-11-02.

6. **Test completeness.** At the developer's request, every task requirement and checklist pitfall was mapped to a
   test ([`test-coverage.md`](test-coverage.md)). The missing ones were added: import racing bookings, identical
   parallel requests, parallel re-imports, the checklist's A/B/C peak example, capacity 3, file-order conflicts, DIA
   duration per provider, extensibility via config, the H2 profile and frontend serving. The import race tests were
   confirmed to fail when the import does not take the provider lock.

7. **Opening hours.** During planning, the developer chose to follow the spec literally, without opening hours. A
   later checklist from the developer listed them as a must. They were added as a documented deviation
   ([ADR 0007](adr/0007-opening-hours.md)): per provider, in local time, with daylight saving handled, and no
   crossing of closing time or midnight. Before choosing the configured hours, Claude checked that every legacy
   appointment falls within them.

8. **Concurrency parity.** Checking the concurrency checklist, Claude measured that H2 gives up waiting for a row lock
   after about 2 s, while PostgreSQL waits indefinitely. Both now use the same explicit lock timeout, and a timeout is
   answered with 503. New tests cover:
   - 409 for the losers of a race at HTTP level;
   - that bookings and imports really wait for the lock;
   - that other providers are not blocked;
   - the same checks on H2.

   Each lock test was confirmed to fail when the lock is removed, on both databases.

9. **Acceptance suite.** The developer supplied a 52-scenario test plan derived from the task text (capacity, grid,
   end time, suggestions, booking, concurrency, import, management, providers, extensibility). It was later extended
   by three overlap shapes (C11 inside, C12 enclosing, C13 touching before), giving 55 scenarios. Claude implemented
   it 1:1 as a separate suite with the scenario ids in the method names. The suite was then validated by planting four
   typical bugs in the production code, and each was caught (see [`test-coverage.md`](test-coverage.md)). The bug
   "new start or end inside an existing appointment" is caught by C12 only.
   - One scenario (S4) assumes that `to` limits the *start* of a suggestion. This project decided that `to` limits the
     *end*, so S4 runs with a wider range and says so in a comment.
   - X1 adds the third provider through an ordinary configuration file, not through test code.
   - The developer asked what the task itself says about the four decisions the scenarios depend on. Claude quoted
     the task and the original spec: all four are left open. The only tension is the literal "Eindeutige Kennung"
     versus the per-provider service id. The developer kept the per-provider id, and the justification was written
     out in [ADR 0002](adr/0002-configuration-driven-catalog.md). The quotes are in
     [`test-coverage.md`](test-coverage.md).

10. **Review against the evaluation criteria.** The packaged JAR was run again, and the README's manual frontend check
    was driven in headless Chrome:
    - all seven functions, the import tab, and the race "slot taken between suggestion and click";
    - the UI shows the 409 reason and refreshes the suggestions;
    - no browser console errors.

    The first attempt at the race case booked successfully. Claude traced this to its own test script: the slot was
    "filled" with LAPs, which the server correctly rejected, so the slot stayed free. The application was right. The
    script was fixed and the check rerun. The review also found a README step that could not be reproduced as
    written; it was rewritten as step 5b.

11. **Fixes after the evaluation review.** The developer asked to fix three of the weak spots found, to explain
    two, then to fix one of those as well:
    - **Local time:** the UI now works in the provider's time zone. The API exposes `timeZone` and `openingHours`.
      The JS conversion was checked in Node across both 2026/27 DST switches, then in headless Chrome with the browser
      deliberately set to America/New_York.
    - **Chunked import:** the import now runs in chunks, so bookings are not blocked. While testing, an expected
      count of 1600 came out as 1598. That was correct behaviour: the test's own booking overlapped imported rows,
      and two rows were rejected for capacity. The test was fixed, and it was confirmed to fail without chunking.
    - **Paging:** the appointment list is paged with `X-Total-Count`.
    - **`CatalogService`:** all provider and service lookups now go through one service. Previously the same "not
      found" check existed in five places.
    - The Ext JS version was explained (license implication) but deliberately left unchanged, at the developer's
      request.

12. **Deviations register.** Prompted by the task's "Flexibility" section, Claude built
    [`deviations.md`](deviations.md).
    - **API additions:** the original and current `rest-api-spec.yml` were parsed and compared mechanically. The
      comparison found additions only.
    - **Behavioural and task-level deviations:** listed by hand.
    - **Each entry** has the original, the change, the reason, the ADR and a test. A script verified that every
      referenced test exists and that every YAML addition is marked "Erweiterung".

    Building the register surfaced three real issues, all fixed:
    - **A wrong claim:** "existing clients keep working" about default paging. At the developer's choice, paging
      became opt-in.
    - **A real bug:** a CSV customer number over 64 characters made the whole import fail with 409. It is now a
      rejected row (`INVALID_CUSTOMER_NUMBER`), test first.
    - **Undocumented behaviour:** `Location`, `413` and the length limit were missing from the YAML.

13. **Senior follow-ups to the register findings.** The developer asked how a senior would fix the three findings,
    measured against the task text. Claude separated the immediate causes from the deeper ones. The developer chose
    two to implement:
    - **Import status:** `RUNNING`/`COMPLETED`/`FAILED`. An aborted import now says where it stopped, and the client
      gets 500 with a link to the report. This was tested by making the scheduler fail for one row via a spy bean.
      A first test attempt failed because stubbing the spy passed through the scheduler's `MANDATORY` transaction
      guard. That was a test-setup issue (the guard was working as intended), fixed by stubbing inside a
      transaction.
    - **Response-level spec check:** every response of every API test is checked against the YAML. It was
      verified by deleting three spec entries one at a time; each was caught.

## What the human did

- Set the scope and constraints: Ext JS sourcing, opening hours (first none, later required), build tool and database.
- Approved the plan and asked for a check against the task before implementation.
- Installed the JDK when the machine turned out to have only a JRE. The assistant is configured never to use root
  privileges.

## What to review critically

All code was generated by the AI and verified by tests and execution, as described above. The design decisions are
explained in the [ADRs](adr/README.md) so they can be judged on their merits. Areas that deserve a reviewer's eye:

- The locking strategy (see [ADR 0003](adr/0003-pessimistic-provider-lock.md))
- The import policy for overbooked legacy rows (see [ADR 0005](adr/0005-csv-import-policy.md))
- The UTC-only UI
