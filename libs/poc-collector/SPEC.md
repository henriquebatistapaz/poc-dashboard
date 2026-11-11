# Multi-Source Collector Mini — Initial Specification

**Status:** Initial implementation delivered for Google News, Google Alerts, WordPress, and conditional Reddit OAuth
**Repository:** Private  
**Scope:** Minimal RSS/Atom CLI collection for permitted feed sources; the other sources remain access-gated.

## 1. Purpose

Define a small, private service that discovers and collects public links and limited metadata from five requested sources:

1. Google News
2. Google Alerts
3. Reddit
4. Issuu
5. WordPress

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
| WordPress | Public RSS 2.0 / Atom feeds exposed by a site | **Implemented.** WordPress documents built-in feeds; a site may disable or restrict them. | User supplies a public HTTPS feed URL; reject private network destinations and redirects. |
| Google News | Public RSS search feed | **Implemented as conditional.** The observed RSS search route has no official Google API contract and may change. | Use the fixed Google News RSS host and query parameters only; do not scrape HTML. Recheck behavior before production use. |
| Google Alerts | RSS delivery for a user-configured alert | **Implemented as conditional.** Current official help documents email alerts but does not document RSS delivery. | Accept only a feed URL explicitly obtained by the user from their account. Treat it as a secret; never commit, return, or log it. |
| Reddit | Official Data API using OAuth | **Conditional.** Reddit requires an approved developer app, registered OAuth, a descriptive User-Agent, and compliance with current Data API terms and rate limits. | Implement OAuth refresh-token auth and the official API reader. Keep disabled unless Reddit access approval and valid app credentials are configured. Never scrape HTML or bypass an approval denial. |
| Issuu | Anonymous public feed/API | **Unverified.** No official, stable tokenless feed or API contract has been established for this project. | Research official documentation and terms first. No HTML scraping or hidden endpoints; mark unavailable if no permitted route exists. |

**Access modes:** Google News and user-supplied RSS/Atom feeds use public feeds. Reddit is the explicit OAuth exception to the original tokenless preference. OAuth credentials and Google Alerts feed URLs are secrets; OAuth does not replace Reddit's access approval or terms.

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
- The Reddit adapter must require explicit approval configuration, client ID/secret, a refresh token, and a truthful descriptive User-Agent before making any collection request.
- The OAuth bootstrap must build a consent URL with cryptographically random state, the `read` scope only, and permanent access; compare callback state in constant time before exchanging authorization codes through Reddit's token endpoint.
- Use OAuth token refresh and the official `oauth.reddit.com` API host; never scrape Reddit HTML. Keep credentials and returned tokens redacted from string representations and logs.
- Keep Reddit access disabled by default. Reddit's external Data API access and app review are platform gates that the code cannot grant.

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
- **Source collectors:** Google News search RSS, account-provided Google Alerts RSS URL (conditional), user-provided public WordPress RSS/Atom URL, and Reddit Data API OAuth reader (conditional).
- **Reddit OAuth:** creates a `read`-only permanent-consent URL with random state, exchanges a user authorization code, then reads client ID, client secret, refresh token, approval flag, and Reddit username from the runtime environment/secret store; refreshes access tokens in memory; sends a truthful descriptive User-Agent; performs no HTML scraping.
- **Source registry/service:** reports capability and prevents collection from Reddit until access approval and credentials are configured; Issuu remains unverified.
- **Output:** CLI emits a minimal JSON array; no persistence or deduplication store is introduced in this slice.
- **Storage:** undecided; start in memory only if a first implementation slice is approved. Choose durable storage only after retention and deletion requirements are agreed.
- **API:** undecided; define endpoints and OpenAPI contract after the first permitted source slice is approved.

Do not add a message broker to the initial parser/collection slice. ActiveMQ Classic is a later option if refresh jobs require asynchronous delivery. Kafka is relevant only if replayable event history or multiple independent consumers become a demonstrated need.

## 7. Delivery plan and status

### Phase 0 — Source feasibility (reviewed for supported feed and OAuth paths)

Confirm each public access path, the applicable terms/robots rules, response format, rate limits, and a small set of allowed test URLs. No adapter code is written in this phase.

### Phase 1 — First permitted feed slice (implemented)

Implemented: Google News and Google Alerts (conditional) plus WordPress RSS/Atom, URL safety checks, deterministic JUnit fixtures, and a CLI. No scheduler, persistence, or external-network-dependent tests.

### Phase 2 — Reddit OAuth (implemented, platform approval pending)

The OAuth client and post reader are implemented and locally tested. Live collection remains blocked until the owner has Reddit Data API access approval, registers an application, and supplies runtime secrets. Issuu stays unverified until an official permitted route is found.

### Phase 3 — API and persistence

Define a small OpenAPI contract and persistence model only after collection behavior, data retention, deletion, and operational needs have been approved.

## 8. Acceptance criteria and implementation status

- All five in-scope sources appear in the capability matrix with a route assessment and an implementation gate.
- Reddit OAuth uses only approved, officially issued credentials and remains disabled until approval is configured.
- No credentials or real private feed URL are included.
- Local JUnit tests cover parsing, URL validation, collector query construction, secret URL redaction, and source gating.
- The repository remains private; `/docs/` and AI-Init generated files are ignored by Git.
- A first implementation slice cannot begin until source feasibility and scope are approved.

## 9. References

- [WordPress: Feeds](https://developer.wordpress.org/advanced-administration/wordpress/feeds/) — documents built-in RSS and Atom feed types and URLs.
- [Google Search Help: Create an alert](https://support.google.com/websearch/answer/4815696) — documents alert creation and notification settings; does not define an RSS API.
- [Google News RSS search route](https://news.google.com/rss/search?q=technology) — observed feed URL example; not an official API reference.
- [Reddit Data API Terms](https://redditinc.com/policies/data-api-terms) — requires use of access information provided by Reddit.
- [Reddit external API FAQ](https://developers.reddit.com/docs/guides/faq) — external integrations must follow Data API access guidance and request approval.
- [Reddit Data API Wiki](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki) — requires registered OAuth and a descriptive User-Agent; publishes current rate limits and warns that linked legacy technical guidance may be outdated.
- [Reddit OAuth2 technical guidance](https://github.com/reddit-archive/reddit/wiki/OAuth2) — OAuth authorization code, access-token, and refresh-token flow; Reddit labels this legacy technical guidance.
- [Reddit User Agreement](https://redditinc.com/policies/user-agreement) — collection is allowed only under applicable terms; scraping without prior written consent is prohibited.
- [RFC 4287: The Atom Syndication Format](https://www.rfc-editor.org/rfc/rfc4287) — future parser format reference.
- [RSS 2.0 Specification](https://www.rssboard.org/rss-specification) — future parser format reference.

**Review note:** This matrix is an engineering starting point, not legal advice. Re-check platform documentation and policies before enabling connectors in production. The Google News RSS route is observed and undocumented; Google Alerts RSS is conditional on the user's account exposing it.
