# Runner / Dashboard Integration Contract

Status: implemented synchronous integration; private repositories, Java 21, Maven, JUnit.

## Scope

Compose the existing RSS/Atom library, source collectors and synthetic social adapters as a plain runner library embedded in dashboard-mini. The dashboard owns the canonical database schema and HTTP security; the runner owns source dispatch, normalization and atomic insert/deduplication. Preserve the legacy standalone social API and its separate table.

## Acceptance criteria

- RSS/Atom, Google News, Google Alerts, WordPress and synthetic Alpha/Beta dispatch to their existing implementations.
- Official Reddit approval/OAuth gating and Issuu unavailability remain enforced.
- A source result becomes canonical source, external ID, title, link and optional publication date; the writer adds collection time.
- PostgreSQL uniqueness enforces first-write-wins `(source, external_id)` across retries, processes and concurrent calls.
- A failed write rolls back the complete batch; the result reports success only after commit.
- No schema creation or dashboard configuration side effects occur when importing the library.
- The dashboard's real Flyway migration, authenticated HTTP collection and item queries pass integration tests against PostgreSQL.
- AI-Init workflow files remain ignored and uncommitted.

## Boundaries

Runs are synchronous, at most 1,000 items and 4,096 input characters. They have no persistent job history or scheduler. RSS IDs are namespaced by the trimmed feed URL hash. Duplicate records are not updated. Titles exceeding the canonical field size are rejected rather than truncated. Legacy social rows are not automatically migrated; only explicit new runs populate the dashboard table.

## Verification

Runner unit/legacy tests: `mvn test`. Real PostgreSQL tests: `mvn -Ppostgres-it verify` with Docker. Cross-project consumer verification lives in dashboard-mini and uses the actual consumer-owned Flyway migration.
