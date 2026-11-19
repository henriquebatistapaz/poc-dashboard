# Multi-Source Collector Mini — Initial Specification

**Status:** Initial implementation delivered for Google News and WordPress tag search
**Repository:** Private  
**Scope:** Minimal RSS/Atom CLI collection for permitted feed sources; unverified sources remain disabled.

## 1. Purpose

Define a small, private service that discovers and collects public links and limited metadata from the requested sources:

1. Google News
2. Issuu
3. WordPress

The service must use only access methods that are currently documented or explicitly permitted. A requested source is not a promise that anonymous collection is technically available or allowed. If a source requires a token, written permission, or a platform-approved integration, its anonymous connector must remain disabled instead of bypassing the restriction.

## 2. Product boundary

### In scope

- A common source-connector contract and source capability status.
- Public links and the minimum metadata needed to identify and deduplicate a result.
- Per-source feasibility checks before implementation.
- Respect for source terms, robots rules, rate limits, privacy, and content removal requests.
- A Java 21 / Maven implementation, following the existing private mini-project conventions.

### Out of scope for this initial implementation slice

- Live requests, web scraping, browser automation, or undocumented endpoint calls.
- Persistence, REST endpoints, scheduling, and message brokers.
- Collecting full article/document bodies, personal profiles, or private content.
- Circumventing login, CAPTCHA, anti-bot controls, rate limits, or access restrictions.

## 3. Source feasibility matrix

| Source | Access route | Current assessment | Implementation gate |
|---|---|---|---|
| WordPress | Public WordPress.com tag RSS feeds | **Implemented.** Searches public `wordpress.com/tag/<slug>/feed/` feeds by normalized keyword. | Normalize query to ASCII lowercase slug (1–200 chars). |
| Google News | Public RSS search feed | **Implemented as conditional.** The observed RSS search route has no official Google API contract and may change. | Use the fixed Google News RSS host and query parameters only; do not scrape HTML. Recheck behavior before production use. |
| Issuu | Anonymous public feed/API | **Unverified.** No official, stable tokenless feed or API contract has been established for this project. | Research official documentation and terms first. No HTML scraping or hidden endpoints; mark unavailable if no permitted route exists. |

**Access modes:** Google News and WordPress tag feeds use public HTTPS RSS/Atom feeds.

## 4. Functional requirements

Future implementation must:

- Represent each source with `AVAILABLE`, `CONDITIONAL`, `UNVERIFIED`, or `UNAVAILABLE` capability status and an evidence/review date.
- Accept only sources whose current capability gate has passed.
- Collect only public item links and limited feed metadata: source, stable item ID when available, title, canonical link, publication time, and retrieval time.
- Normalize links and deduplicate using `(source, stable source item ID)`, falling back to a normalized canonical URL only when no stable ID exists.
- Preserve source attribution and the original item URL.
- Stop a connector after access denial, robots prohibition, repeated throttling, or a policy-related response; do not retry aggressively.
- Allow each connector to be disabled without affecting other permitted sources.
- Record enough status and error information to diagnose a feed without logging credentials, cookies, alert feed URLs, or full content.

## 5. Non-functional and safety requirements

- Enforce HTTPS by default; any HTTP exception must be explicit and justified.
- Prevent SSRF: reject loopback, private, link-local, multicast, reserved IP ranges, and unsafe DNS resolutions; repeat checks after redirects and DNS changes.
- Use strict connect/read timeouts, bounded response size, bounded redirects, per-host concurrency, and conservative rate limits.
- Do not forward authorization, cookies, or user-supplied headers across origins or redirects.
- Parse XML with DTDs and external entities disabled; impose depth, item-count, and document-size limits.
- Store only minimal metadata; define retention, deletion/takedown handling, and source-specific policy before production use.
- Keep credentials and feed URLs with bearer-like semantics in a secret store or local ignored environment, never in Git or logs.
- Keep the repository private until the owner explicitly chooses otherwise.

## 6. Implemented architecture

- **Source registry:** source name, capability state, evidence reference, and enablement flag.
- **Connector interface:** validates configuration, fetches within configured limits, and returns normalized candidates.
- **Feed parser:** secure JDK StAX parser for RSS and Atom, with DTD/external entities disabled, item links restricted to HTTP(S), and a 100-item cap.
- **Fetch client:** JDK HTTP client with 5-second connect timeout, 10-second request timeout, 1 MB response cap, and redirects disabled.
- **Source collectors:** Google News search RSS and public WordPress.com tag RSS search.
- **Source registry/service:** reports capability and prevents collection from disabled sources; Issuu remains unverified.
- **Output:** CLI emits a minimal JSON array; no persistence or deduplication store is introduced in this slice.
- **Storage:** undecided; start in memory only if a first implementation slice is approved. Choose durable storage only after retention and deletion requirements are agreed.
- **API:** undecided; define endpoints and OpenAPI contract after the first permitted source slice is approved.

Do not add a message broker to the initial parser/collection slice. ActiveMQ Classic is a later option if refresh jobs require asynchronous delivery. Kafka is relevant only if replayable event history or multiple independent consumers become a demonstrated need.

## 7. Delivery plan and status

### Phase 0 — Source feasibility (reviewed for supported feed and OAuth paths)

Confirm each public access path, the applicable terms/robots rules, response format, rate limits, and a small set of allowed test URLs. No adapter code is written in this phase.

### Phase 1 — First permitted feed slice (implemented)

Implemented: Google News, WordPress tag search, URL safety checks, deterministic JUnit fixtures, and a CLI.

### Phase 2 — API and persistence

Define a small OpenAPI contract and persistence model only after collection behavior, data retention, deletion, and operational needs have been approved.

## 8. Acceptance criteria and implementation status

- Active in-scope sources appear in the capability matrix with a route assessment and an implementation gate.
- No credentials or real private feed URL are included.
- Local JUnit tests cover parsing, URL validation, collector query construction, secret URL redaction, and source gating.
- The repository remains private; `/docs/` and AI-Init generated files are ignored by Git.
- A first implementation slice cannot begin until source feasibility and scope are approved.

## 9. References

- [WordPress: Feeds](https://developer.wordpress.org/advanced-administration/wordpress/feeds/) — documents built-in RSS and Atom feed types and URLs.
- [Google News RSS search route](https://news.google.com/rss/search?q=technology) — observed feed URL example; not an official API reference.
- [RFC 4287: The Atom Syndication Format](https://www.rfc-editor.org/rfc/rfc4287) — future parser format reference.
- [RSS 2.0 Specification](https://www.rssboard.org/rss-specification) — future parser format reference.

**Review note:** This matrix is an engineering starting point, not legal advice. Re-check platform documentation and policies before enabling connectors in production. The Google News RSS route is observed and undocumented.
