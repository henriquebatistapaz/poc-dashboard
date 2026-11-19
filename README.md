# POC Dashboard

Private Java 27 / Maven backend on Spring Boot 4.1.2 that embeds `poc-collector-runner` to collect RSS/Atom, supported source feeds and synthetic social fixtures, persist normalized items and query them. PostgreSQL/Flyway owns the canonical schema. Lombok reduces entity/constructor boilerplate; request/response models use records.

## Vue panel — Portuguese, English and Spanish

The local panel at `http://localhost:8085` includes administrator login, a feed test form, source selection, real RSS/Atom examples, new/duplicate counts, recent asynchronous collection history (including tracking a pending run again after login), filtered/paginated items and a detail dialog linking to the original. Choose RSS/Atom, Google News, WordPress, Mastodon, Lemmy, DEV.to, Hacker News, Stack Exchange, GitHub, TabNews, Wikipedia, GitLab or Lobsters and enter a feed URL, search term or Lobsters tag. The Vue app calls the authenticated Dashboard endpoint, which embeds the runner and its source libraries. Successful collection refreshes the list automatically. Google Alerts collection and scheduling are removed; saved items and run history remain readable.

All user-facing labels, validation, errors, dates and counts support `pt-BR`, `en` and `es`. Change language on the login screen or in the workspace without reloading. Only the selected locale is saved in browser storage. JWT and login credentials remain in memory; reloading, signing out or session expiration requires a new login. Source inputs are not persisted in browser storage; the collection history stores them encrypted at rest and returns them only through JWT-protected endpoints. History inputs are encrypted with a key derived from `DASHBOARD_JWT_SECRET`, so rotating that secret also makes older collection inputs unreadable.

See [frontend/README.md](frontend/README.md) for development, browser tests and translation conventions. The backend remains Java/Maven; Node.js 22.12+ is needed only for frontend development/builds. Compose builds the UI with Node 24 and serves it through Nginx, proxying API calls on the same origin without a permissive CORS rule.

## Working flow

1. Log in through `POST /v1/auth/login` using the runtime-configured administrator.
2. Send `POST /v1/collections` with a Bearer JWT, a `source` enum and `input` string.
3. The embedded runner calls the existing RSS/source/social libraries and commits new items to PostgreSQL.
4. Query `GET /v1/items` or `GET /v1/items/{id}` using the JWT or a read-only `X-API-Key`.

No separate runner HTTP server is needed. Dashboard migrations own `collected_item`. To import historical rows from `social_posts` when both tables are in the same PostgreSQL database, review and run [`scripts/migrate-social-posts.sql`](scripts/migrate-social-posts.sql); it is idempotent and skips existing `(source, external_id)` pairs. The target model does not retain the legacy author field. See [SPEC.md](SPEC.md) for the target architecture and remaining phases.

### Collection request

```json
{"source":"GOOGLE_NEWS","input":"Java Spring Boot"}
```

Sources: `RSS` (public RSS/Atom URL), `GOOGLE_NEWS` (query), `WORDPRESS` (keyword/tag query), `ALPHA`/`BETA` (synthetic fixture JSON). `ISSUU` remains disabled. Source inputs are limited to 4,096 characters; a synchronous run supports at most 1,000 items. Requests use enum names exactly as shown.

The response and recent-history records include the original `input` so the dashboard can show which search term or RSS link started each run. Inputs are encrypted in the database. Duplicates use `(source, external_id)` and preserve the first stored item. RSS IDs additionally include a feed URL hash namespace. The complete batch rolls back if persistence fails. Collections run asynchronously and their status and counts remain in the run history.

Errors: 400 invalid input/item fields, 401 missing/invalid credentials, 403 a read-only key attempts collection, 422 disabled/unconfigured source, 502 upstream collection failure, 503 storage unavailable or admin login not configured. Secret input is not reflected in collection errors.

### Queries and Swagger

`GET /v1/items` accepts `source`, case-insensitive title query `q`, collection instants `from`/`to`, zero-based `page` and `size` (default 20, clamped to 1–100). Results sort by collection time and ID descending. Reversed date ranges return 400. Detail returns 404 for an unknown ID.

Swagger UI: `/swagger-ui.html`; OpenAPI: `/v3/api-docs`. Both are available without authentication for local development. Swagger Authorize supports the read API key and Bearer JWT.

## Build the private libraries

Clone all four private repositories as siblings: `poc-collector`, `poc-rss-collector`, `poc-collector-runner`, `poc-dashboard`. From this repository:

```sh
./scripts/build-local.sh
```

It runs Maven installs in dependency order (collector, RSS, runner), then packages the dashboard. Alternative locations can be supplied with `COLLECTOR_REPO`, `RSS_REPO` and `RUNNER_REPO`. OpenJDK 27.0.1.0.3 and Maven 3.9 or later are required. Artifacts resolve through the local Maven repository; no private Maven package registry is configured.

## Local credentials

Copy `.env.example` to `.env` and fill it locally (the file is ignored). Generate separate random values for `DB_PASSWORD`, `DASHBOARD_API_KEY` and `DASHBOARD_JWT_SECRET`, for example with `openssl rand -hex 32`. Set `DASHBOARD_ADMIN_EMAIL` and generate a BCrypt password hash interactively:

```sh
./scripts/hash-admin-password.sh
```

Paste the printed hash as a single-quoted `.env` value, e.g. `DASHBOARD_ADMIN_PASSWORD_HASH='$2a$...'`. The helper prompts without echo; it does not accept the password as a command argument. Passwords must contain 1–72 UTF-8 bytes. The first administrator is bootstrapped into `user_account`; public registration is disabled. Administrators can create `ADMIN` and `OPERATOR` accounts with `POST /v1/admin/users`, list accounts with `GET /v1/admin/users`, and deactivate another account with `POST /v1/admin/users/{id}/deactivate`. Passwords are stored as BCrypt hashes and never returned by the API.

Login returns `accessToken`, `tokenType=Bearer`, `expiresIn=900`. JWTs use HS256 and verify issuer, signature and expiration. `DASHBOARD_JWT_SECRET` must contain at least 32 UTF-8 bytes. Login returns 503 until the database has an enabled user and JWT signing is configured. A missing/incorrect/unconfigured API key never grants access. API keys cannot run collections.

## Run with Docker Compose

After building the jar and setting local `.env` values:

```sh
docker compose up --build -d
```

Open the Vue panel at `http://localhost:8085` or Swagger at `http://localhost:8084/swagger-ui.html`. Compose starts PostgreSQL 16, the Dashboard with the embedded runner, and the Vue/Nginx frontend. Database and API ports bind to loopback; ports default to 15432 (database), 8084 (API) and 8085 (panel). Set `FRONTEND_PORT` to change the panel port. A named database volume preserves collected items. `docker compose down` stops the stack without deleting that volume.

Alternatively start only PostgreSQL with `docker compose up -d postgres`; export `DB_URL=jdbc:postgresql://localhost:15432/dashboard`, `DB_USER=dashboard`, `DB_PASSWORD` and the dashboard credential variables, then run `mvn spring-boot:run -Dspring-boot.run.profiles=dev,local`. Maven does not load `.env` automatically. The Java application defaults to listening on loopback. The local profile provides the `local` / `local` account.

## Verification

```sh
mvn test
# Requires Docker; runs actual HTTP + PostgreSQL integration tests.
mvn -Ppostgres-it verify
```

Ordinary tests use H2. PostgreSQL tests use the real Flyway migration, real HTTP login/collection/query endpoints, actual runner and parsers; only upstream fetches/DNS are deterministic fixtures. They verify RSS/Atom, Google News, WordPress, the ten public REST sources and Alpha/Beta using local fixtures; source restrictions remain active. These deterministic tests do not prove live Google service availability.

A local Compose smoke run also collected real BBC RSS, XKCD Atom, Google News and WordPress feeds (79 items in that run). Repeating each collection inserted zero duplicates, and authenticated queries returned the stored items. External feed contents/counts can change.

Docker API defaults to 1.44 in the integration profile (`-Ddocker.api.version=...` overrides it).

## Pending

Historical social-row migration and public deployment review. Scheduling is available when `DASHBOARD_SCHEDULE_KEY` is configured with 64 hexadecimal characters; scheduled inputs are encrypted at rest. The repositories stay private. AI-Init assets and generated `/docs/` are excluded from Git.
