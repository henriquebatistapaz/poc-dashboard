# Dashboard Mini — Product and Integration Specification

**Status:** Draft for product and architecture review; no application code is implemented.
**Repository:** Private.
**Purpose of this document:** define a small first release and the decisions required before implementation. Technology and deployment choices below are proposals unless explicitly marked as a requirement.

## 1. Product goal

Provide one private place to request and inspect collection from the existing RSS and source-collector projects. The service should let an owner configure collection inputs, trigger one-off runs, review normalized results, and later schedule recurring runs.

This project is an orchestration and query layer. It must not reimplement RSS parsing or source-specific collection rules already owned by the other repositories.

## 2. Related projects and boundaries

| Project | Current responsibility | Dashboard relationship |
|---|---|---|
| `rss-collector-mini` | Validates and parses RSS 2.0 and Atom 1.0 feeds. | Use its feed validation/parsing capability for RSS/Atom input. It is not the system of record for collected results. |
| `collector-mini` | Collects Google News, user-supplied Google Alerts feeds, public WordPress feeds, and Reddit through the official OAuth/Data API path. | Invoke only supported source operations. Reddit stays disabled until the owner has platform approval and supplies credentials. Issuu is not supported until a permitted official route is verified. |
| `collector-runner-mini` | Adapts collection results, normalizes and deduplicates social posts, and persists them. | Reuse its persistence and duplicate handling where applicable; do not create a competing copy of the same item store. |
| `dashboard-mini` | This project. | Owns user-facing configuration, orchestration status, and the query API/UI. Exact data ownership remains an open decision. |

### Integration principles

- Keep source-specific fetching, parsing, OAuth, rate limits, and access rules in the owning collector.
- Keep one canonical identity for each collected item. Define whether all source types share a common item model before implementing storage.
- Do not access another project's database tables directly until ownership, schema versioning, and read/write responsibilities are agreed.
- Prefer a small synchronous in-process integration for the first local slice if dependency boundaries and Spring versions permit it. HTTP integration remains an alternative if independent deployment is required. This choice is not approved yet.
- If asynchronous delivery becomes necessary, use ActiveMQ Classic as the first broker option. Consider Kafka only when replayable history or multiple independent consumers are a demonstrated requirement. Do not introduce RabbitMQ.

## 3. Users and core workflows

### Primary user

One owner operates the private installation. The first release does not need public signup, teams, or role management.

### Workflow A — configure a source

1. The owner registers a supported source and its input (for example, an RSS/Atom URL, Google News query, Google Alerts feed URL, WordPress feed URL, or Reddit query/subreddit).
2. The system validates the input using the owning collector's validation rules.
3. The system shows whether the source is ready, disabled, invalid, or waiting on external approval/credentials.

### Workflow B — run collection

1. The owner starts a one-off collection for a configured source.
2. The system records a run ID and returns promptly with an accepted/in-progress result if the operation is not immediate.
3. The owner can inspect status, start/end time, counts, and a safe error summary.
4. Repeating a run must not create duplicate canonical items.

### Workflow C — search results

The owner lists and views collected items, filtering by source, date range, and text. Results include a safe link to the source item and collection metadata.

### Workflow D — schedule a collection (later MVP increment)

The owner enables, pauses, edits, or deletes a recurring schedule. A schedule uses a bounded interval and the same validation, source policies, deduplication, and run reporting as a one-off collection.

## 4. Scope by phase

### Phase 0 — contracts and decisions (current)

- Confirm the shared item model and canonical persistence owner.
- Confirm in-process library versus HTTP integration.
- Confirm whether the first deliverable includes an API only or an API and web UI.
- Define authentication for a private single-owner deployment, secrets handling, retention, and deletion.
- No runtime code is in scope in this phase.

### Phase 1 — smallest useful local slice (proposed)

- One private owner; no public registration.
- Validate a supplied RSS/Atom feed, start one manual collection, and query results.
- Support source types only when the corresponding existing project exposes a stable callable contract.
- Run history with `QUEUED`/`RUNNING`/`SUCCEEDED`/`PARTIAL`/`FAILED` status, timestamps, item counts, and sanitized error details.
- OpenAPI documentation and local Compose only if the chosen architecture needs the database or broker locally.
- Automated tests for validation, orchestration, duplicate handling, authorization, and failure reporting.

### Phase 2 — recurring collection (proposed, not committed)

- Create, pause, resume, update, and delete schedules.
- Enforce a configurable minimum interval and prevent overlapping runs for the same schedule.
- Record each schedule execution as a run with the same status model as manual runs.
- Add a broker only if synchronous execution no longer meets the accepted latency/reliability needs. If selected, start with ActiveMQ Classic.

### Explicitly out of scope until separately approved

- Rewriting or forking the collectors, their parsers, OAuth flows, or source policy logic.
- Public hosting, public signup, multi-tenant accounts, billing, or a public API.
- Alerts by email/Telegram, analytics, dashboards with charts, and AI summarization.
- Browser scraping, bypassing source restrictions, or enabling Reddit before required approval and credentials.
- Kafka, unless replay or multiple independent consumers become a concrete need.

## 5. Functional requirements

- **FR-01 Source registry:** create, view, update, enable/disable, and delete a configured source input. Do not persist secrets in source names, URLs shown in logs, or run error messages.
- **FR-02 Input validation:** validate URLs and source-specific inputs through the owning collector. Reject unsupported schemes and unsafe network targets according to that collector's protections. Do not fetch arbitrary URLs from the dashboard outside the validation/collector contract.
- **FR-03 Manual run:** start a collection for an enabled source and return a stable run ID. Repeated requests with the same idempotency key must not start duplicate work during the documented idempotency window.
- **FR-04 Run status:** expose status, timestamps, counts (`discovered`, `new`, `duplicate`, `rejected` where available), and a sanitized failure reason. Never expose access tokens, refresh tokens, passwords, API keys, or secret feed URLs.
- **FR-05 Result list:** provide a paginated list with source, text query, and date range filters. Page size defaults to a small value and is capped at 100.
- **FR-06 Result detail:** return normalized source identity, title, original URL, publication time when available, collection time, and the project/source attribution.
- **FR-07 Schedule management (Phase 2):** create, list, pause/resume, update, and delete recurring source jobs. Reject intervals below the configured minimum and report the next planned run.
- **FR-08 Health and readiness:** expose a minimal health endpoint and report dependency readiness without leaking configuration or secrets.
- **FR-09 OpenAPI:** publish an OpenAPI contract for all supported endpoints. Document authentication, pagination, validation errors, and run states.
- **FR-10 Auditability:** record who initiated a run (the single owner in the first release), when it ran, and its outcome. Do not log raw credentials or full secret-bearing inputs.

## 6. Source availability and policy gates

The dashboard presents availability; it does not bypass the source project or the source platform.

| Source | Current path | Dashboard behavior |
|---|---|---|
| RSS/Atom | `rss-collector-mini` | Accept a user-provided feed URL; validate and parse RSS 2.0 or Atom 1.0 using the existing project. |
| Google News | `collector-mini` | Use its supported RSS route; show that the upstream endpoint is undocumented and may change. |
| Google Alerts | `collector-mini` | Accept only a feed URL supplied by the owner, if their account exposes one. Treat the URL as a secret. |
| WordPress | `collector-mini` | Use a public RSS/Atom feed provided by the owner, subject to existing URL safety checks. |
| Reddit | `collector-mini` | Disabled until external Data API approval and valid OAuth configuration are confirmed. Use the official API only; no HTML scraping. |
| Issuu | None confirmed | Show as unavailable. Do not implement until an official, permitted, stable endpoint is verified. |

The source list and gates must be verified against the current source projects before implementation, since upstream access and policy can change.

## 7. Data and persistence proposal

These are logical records, not an approved physical schema:

- **ConfiguredSource:** source type, non-secret display name, input reference, enabled state, created/updated timestamps. Secret values are held in an environment/secret store and referenced by key where possible.
- **CollectionRun:** run ID, source reference, trigger type (`MANUAL`/`SCHEDULED`), status, start/end timestamps, counts, idempotency key, sanitized error code/message.
- **CollectedItem:** canonical source identity, stable external ID when available, title, original URL, publication timestamp when available, collection timestamp, minimal metadata required for search and deduplication.
- **Schedule (Phase 2):** source reference, interval/cron decision, enabled state, next/last run, timestamps.

Before implementation, choose exactly one owner for `CollectedItem` writes and migrations. The dashboard should not duplicate results into a second authoritative store. Define uniqueness/deduplication as `(source, stable external ID)` with a normalized URL fallback only where a source has no stable ID, subject to confirmation with the owning project.

Retention, user-initiated deletion, and backup behavior must be decided before storing production collection data. Until then, the service remains local/private and uses only disposable development data.

## 8. API proposal

Versioned JSON API; paths are initial candidates and require an OpenAPI review:

- `POST /v1/sources` — register a source input.
- `GET /v1/sources` — list configured sources and readiness state.
- `POST /v1/sources/{sourceId}/validate` — validate the configured input without starting a collection.
- `POST /v1/collections` — start a manual run; accepts a source ID and optional idempotency key.
- `GET /v1/collections/{runId}` — inspect run status and outcome.
- `GET /v1/items` and `GET /v1/items/{itemId}` — search and view normalized results.
- `GET /v1/health` — liveness; readiness details remain private and minimal.
- Phase 2: `/v1/schedules` resource operations for recurring collection.

Expected errors include invalid input, unsupported/disabled source, missing platform approval/configuration, rate limit, duplicate request, unavailable dependency, and internal failure. Error responses must use stable error codes and sanitized messages.

## 9. Security, privacy, and operations

- Keep the deployment private by default. Do not deploy publicly until the authentication model, TLS, secret storage, rate limiting, and authorization checks have been reviewed.
- For a local single-owner prototype, choose one explicit protection model (for example, local-only binding plus a configured API key, or an authenticated UI). Do not assume JWT, API key, or both until approved.
- Store Reddit OAuth credentials, API keys, database credentials, and secret Google Alerts URLs outside Git and outside ordinary logs.
- Redact secrets in request logging, exceptions, run details, and OpenAPI examples.
- Avoid SSRF: delegate URL fetching to existing collectors with their safety controls; do not add an unrestricted dashboard fetch endpoint.
- Bound page size, run duration, response size, and source-specific concurrency. Respect upstream rate limits and stop safely on denial.
- Provide an owner-controlled way to delete a configured source and, after retention policy is set, its associated items and run history.

## 10. Acceptance criteria for Phase 1

Phase 1 is acceptable when all of the following are true:

1. A private local instance can register and validate a permitted RSS/Atom input using `rss-collector-mini`.
2. A manual run produces a run ID, transitions through documented states, and exposes sanitized counts and errors.
3. A successful run makes normalized items queryable with pagination and the approved filters.
4. Repeating a request with the same idempotency key does not create a second active run; duplicate source items do not create duplicate canonical records.
5. Unsupported sources, Reddit without its approval/configuration gate, and unsafe/invalid inputs fail clearly without a network-policy bypass.
6. Authentication/protection is in place for every non-health endpoint before the service binds beyond localhost or is deployed.
7. OpenAPI matches implemented behavior, and automated tests cover success, validation, authorization, deduplication, timeout/failure, and secret redaction.
8. All involved repositories remain private; AI-Ready files and generated `/docs/` remain excluded from commits.

## 11. Decisions required before implementation

1. Which source project owns the canonical collected-item table and schema migrations?
2. Should `dashboard-mini` call `collector-runner-mini` in-process or through HTTP? What callable API does each project expose?
3. Is Phase 1 API-only, or should a web UI be included? If included, should it live in this repository or a separate frontend repository?
4. What is the minimum schedule interval, maximum concurrent runs, run timeout, and overlap policy for Phase 2?
5. What retention period and deletion behavior apply to items, run history, and source configurations?
6. Which local protection model is preferred, and what must be in place before any remote deployment?
7. Which source connectors are included in the first slice? Reddit requires external approval and configured credentials; Issuu remains unavailable pending verification.
8. Is synchronous execution sufficient initially? Add ActiveMQ Classic only if accepted run latency or reliability needs require queuing; introduce Kafka only for replay or multiple independent consumers.

## 12. Candidate technology baseline

Subject to the decisions above: Java 21, Maven, Spring Boot, JUnit 5, PostgreSQL only if this service owns data that needs persistence, and Springdoc OpenAPI. Vue is optional until a UI is approved. Docker Compose is for local development only. No candidate in this section is authorization to implement it.
