# TODO

## Specification phase

- [x] Define the five in-scope sources and mark access feasibility honestly.
- [x] Set privacy, terms-of-service, robots, rate-limit, and secret-handling constraints.
- [x] Keep AI-Init generated files and `/docs/` out of Git.
- [x] Review source capability for the first feed slice; keep undocumented or OAuth-only routes gated.

## Before implementation

- [x] Implement Google News search RSS with an explicit conditional capability status; endpoint stability still needs monitoring.
- [x] Add Google Alerts RSS URL ingestion gated to user-provided URLs; determine separately whether the user's account exposes an RSS URL.
- [x] Implement public WordPress RSS/Atom collection with URL safety checks.
- [ ] Complete Reddit's external Data API access approval and configure app credentials locally before live collection.
- [x] Implement Reddit OAuth refresh-token flow with fail-closed approval and credential checks.
- [x] Implement Reddit `/new` post collection with the `read` scope and local JUnit fixtures.
- [x] Research an official permitted Issuu route; keep the source disabled because no public publication search/feed route is documented.
- [x] Select and implement the initial feed vertical slice: Google News, conditional Google Alerts, and WordPress.
- [x] Approve and implement the REST/OpenAPI, persistence, scheduling, and retention requirements for the local service.

## Later implementation slices

- [x] Create a Java 27 / Maven command-line collector.
- [x] Add RSS/Atom parser tests, source-specific tests, bounded HTTP fetcher, and URL safety checks.
- [ ] Revisit the Google News RSS URL format if Google changes the undocumented endpoint.
- [ ] Confirm whether this Google Alerts account exposes an RSS feed; keep the URL in a secret store/local environment only.
- [ ] Obtain Reddit's Data API approval and register the collector app; then configure its secrets in the local runtime environment (external approval is still pending).
- [x] Pin WordPress feed requests to checked public DNS results and reject unsafe re-resolution before connecting.
- [x] Add durable local storage with deduplication, encrypted scheduled inputs, and configurable item retention.
- [ ] Consider ActiveMQ Classic only if refresh jobs need asynchronous delivery. Consider Kafka only if replay or multiple independent consumers become a real requirement.
- [x] Add an authenticated local REST API and OpenAPI contract after threat-reviewing the network fetch path.
