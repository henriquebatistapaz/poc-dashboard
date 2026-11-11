# Collector Runner Mini

Private Java 27 / Maven 3.9 library and standalone synthetic-post API. Lombok supplies entity getters/protected constructors and constructor injection; immutable transport types remain Java records. Spring Boot 4.1.2 and Lombok 1.18.48 are pinned for Java 27 support.

## Dashboard integration

`CollectionRunner` composes the existing `poc-collector` and `poc-rss-collector` libraries with a PostgreSQL `CollectedItemWriter`. The dashboard embeds the runner in its own process; no HTTP call between projects or message broker is required.

| Source | Input | Behavior |
| --- | --- | --- |
| `RSS` | Public RSS 2.0 / Atom 1.0 URL | Reuses the RSS library's bounded HTTP fetcher and parser |
| `GOOGLE_NEWS` | Search query, up to 200 characters | Reuses the existing Google News collector |
| `GOOGLE_ALERTS` | User-owned Google Alerts feed URL | Treat the URL as a secret; do not log or commit it |
| `WORDPRESS` | Public HTTPS feed URL | Reuses the existing WordPress URL policy and collector |
| `REDDIT` | Subreddit | Requires official approval and all existing OAuth environment settings |
| `ALPHA`, `BETA` | Synthetic fixture JSON | Reuses the existing social adapters, not real social providers |
| `ISSUU` | Any input | Unavailable until a permitted route is verified |

One manual run fetches, parses, validates and writes at most 1,000 items. It returns `source`, `fetched`, `inserted` and `duplicates`. Input is capped at 4,096 characters. Longer social text requires a caller-selected title excerpt (maximum 1,000 characters); the library does not silently truncate.

### Database contract

`poc-dashboard` owns Flyway migrations for `collected_item`. The runner only inserts through a supplied `DataSource`; it creates no canonical tables. Required fields: source (40 chars), external ID (2,000), link (2,000); optional title (1,000) and publication instant; collection time is assigned by the runner. Social text maps to title, permalink to link; author/full social text remain outside this dashboard read contract.

A unique `(source, external_id)` key and PostgreSQL `ON CONFLICT DO NOTHING` ensure first-write-wins deduplication, including concurrent writers. A repeated item never overwrites its original content. Each batch commits in its own JDBC transaction and rolls back completely on failure. Do not call it expecting participation in an outer Spring transaction. RSS IDs include a SHA-256 namespace of the trimmed feed URL so separate feeds can reuse the same GUID; changing that URL creates a different namespace.

The existing standalone `social_posts` table/API is preserved. Historical social rows are not migrated or copied automatically. New Alpha/Beta runs through the dashboard write directly to `collected_item`. The runner's standalone defaults live in `collector-runner.properties`, loaded only by its own application; they cannot replace the dashboard's database configuration.

## Build and tests

Install the sibling `poc-collector` and `poc-rss-collector` libraries with `mvn install`, then:

```sh
mvn clean install
# Docker required; fails on missing Docker rather than silently skipping.
mvn -Ppostgres-it verify
```

The profile tests PostgreSQL 16 deduplication, concurrent writers, rollback and timestamps. Ordinary JUnit tests cover source dispatch, RSS/Atom mapping, social adapters, field validation, source restrictions and legacy APIs. The profile defaults to Docker API 1.44; override with `-Ddocker.api.version=...` if necessary.

The main jar is a plain Maven library; the additional `-exec.jar` runs the standalone application. See the dashboard README for ordered builds and the integrated Compose setup.

## Legacy standalone synthetic-post API

`POST /v1/posts` accepts a `NormalizedSocialPost` and returns 201 for an insertion or 200 for a duplicate. `GET /v1/posts` lists persisted posts by publication date. These routes operate on `social_posts`, not the dashboard table.

```sh
docker compose up -d postgres
mvn spring-boot:run
```

Defaults: database `social_adapter` on localhost:5432; override `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Swagger: `http://localhost:8080/swagger-ui/index.html`. This legacy API has no new authentication layer and is for local development; use the authenticated Dashboard for the integrated workflow.

## Pending

Scheduled/background execution, historical social-row migration, durable run history, retention operations and a user interface are future work. ActiveMQ Classic is the work-queue option if asynchronous execution is approved; Kafka is reserved for replay or multiple independent consumers. No AI-Init assets or generated `/docs/` are committed.
