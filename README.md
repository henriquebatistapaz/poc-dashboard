# Dashboard Mini

Private Java 21 / Maven backend that embeds `collector-runner-mini` to collect RSS/Atom, supported source feeds and synthetic social fixtures, persist normalized items and query them. PostgreSQL/Flyway owns the canonical schema. Lombok reduces entity/constructor boilerplate; request/response models use records.

## Working flow

1. Log in through `POST /v1/auth/login` using the runtime-configured administrator.
2. Send `POST /v1/collections` with a Bearer JWT, a `source` enum and `input` string.
3. The embedded runner calls the existing RSS/source/social libraries and commits new items to PostgreSQL.
4. Query `GET /v1/items` or `GET /v1/items/{id}` using the JWT or a read-only `X-API-Key`.

No separate runner HTTP server is needed. The standalone runner's old `social_posts` table is independent; existing rows are not copied automatically. Dashboard migrations own `collected_item`. See [SPEC.md](SPEC.md) for the target architecture and remaining phases.

### Collection request

```json
{"source":"GOOGLE_NEWS","input":"Java Spring Boot"}
```

Sources: `RSS` (public RSS/Atom URL), `GOOGLE_NEWS` (query), `GOOGLE_ALERTS` (user-owned secret feed URL), `WORDPRESS` (public HTTPS feed URL), `REDDIT` (subreddit with approved OAuth access), `ALPHA`/`BETA` (synthetic fixture JSON). `ISSUU` remains disabled. Do not log, share or commit Google Alerts URLs. Source inputs are limited to 4,096 characters; a synchronous run supports at most 1,000 items. Requests use enum names exactly as shown.

Response: `source`, `fetched`, `inserted`, `duplicates`. Duplicates use `(source, external_id)` and preserve the first stored item. RSS IDs additionally include a feed URL hash namespace. The complete batch rolls back if persistence fails. Calls are synchronous; there is no background job or persistent run history yet.

Errors: 400 invalid input/item fields, 401 missing/invalid credentials, 403 a read-only key attempts collection, 422 disabled/unconfigured source, 502 upstream collection failure, 503 storage unavailable or admin login not configured. Secret input is not reflected in collection errors.

### Queries and Swagger

`GET /v1/items` accepts `source`, case-insensitive title query `q`, collection instants `from`/`to`, zero-based `page` and `size` (default 20, clamped to 1–100). Results sort by collection time and ID descending. Reversed date ranges return 400. Detail returns 404 for an unknown ID.

Swagger UI: `/swagger-ui.html`; OpenAPI: `/v3/api-docs`. Both are available without authentication for local development. Swagger Authorize supports the read API key and Bearer JWT.

## Build the private libraries

Clone all four private repositories as siblings: `collector-mini`, `rss-collector-mini`, `collector-runner-mini`, `dashboard-mini`. From this repository:

```sh
./scripts/build-local.sh
```

It runs Maven installs in dependency order (collector, RSS, runner), then packages the dashboard. Alternative locations can be supplied with `COLLECTOR_REPO`, `RSS_REPO` and `RUNNER_REPO`. Java 21 and Maven are required. Artifacts resolve through the local Maven repository; no private Maven package registry is configured.

## Local credentials

Copy `.env.example` to `.env` and fill it locally (the file is ignored). Generate separate random values for `DB_PASSWORD`, `DASHBOARD_API_KEY` and `DASHBOARD_JWT_SECRET`, for example with `openssl rand -hex 32`. Set `DASHBOARD_ADMIN_EMAIL` and generate a BCrypt password hash interactively:

```sh
./scripts/hash-admin-password.sh
```

Paste the printed hash as a single-quoted `.env` value, e.g. `DASHBOARD_ADMIN_PASSWORD_HASH='$2a$...'`. The helper prompts without echo; it does not accept the password as a command argument. Passwords must contain 1–72 UTF-8 bytes. Admin credentials are runtime configuration, not an `app_user` table. No registration endpoint is provided.

Login returns `accessToken`, `tokenType=Bearer`, `expiresIn=900`. JWTs use HS256 and verify issuer, signature and expiration. `DASHBOARD_JWT_SECRET` must contain at least 32 UTF-8 bytes. Missing admin configuration disables login (503), while read-only API-key mode remains possible. A missing/incorrect/unconfigured API key never grants access. API keys cannot run collections.

Reddit stays disabled by default. Only configure `REDDIT_API_ACCESS_APPROVED`, `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET`, `REDDIT_REFRESH_TOKEN`, `REDDIT_USERNAME` after official access approval. Credentials are passed at runtime and are never committed.

## Run with Docker Compose

After building the jar and setting local `.env` values:

```sh
docker compose up --build -d
```

Open `http://localhost:8084/swagger-ui.html`. Compose starts PostgreSQL 16 and the Dashboard with the embedded runner. Database and API ports bind to loopback; ports default to 15432 and 8084. A named database volume preserves collected items. `docker compose down` stops the stack without deleting that volume.

Alternatively start only PostgreSQL with `docker compose up -d postgres`; export `DB_URL=jdbc:postgresql://localhost:15432/dashboard`, `DB_USER=dashboard`, `DB_PASSWORD` and the dashboard credential variables, then run `mvn spring-boot:run`. Maven does not load `.env` automatically. The Java application defaults to listening on loopback.

## Verification

```sh
mvn test
# Requires Docker; runs actual HTTP + PostgreSQL integration tests.
mvn -Ppostgres-it verify
```

Ordinary tests use H2. PostgreSQL tests use the real Flyway migration, real HTTP login/collection/query endpoints, actual runner and parsers; only upstream fetches/DNS are deterministic fixtures. They verify RSS/Atom, Google feeds, WordPress and Alpha/Beta; source restrictions remain active. These deterministic tests do not prove live Google/Reddit service availability.

A local Compose smoke run also collected real BBC RSS, XKCD Atom, Google News and WordPress feeds (79 items in that run). Repeating each collection inserted zero duplicates, and authenticated queries returned the stored items. External feed contents/counts can change. Google Alerts was validated with fixtures because a user-owned secret feed was not supplied; live Reddit remains gated by official approval and credentials.

For Colima set `DOCKER_HOST` to its socket and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. Docker API defaults to 1.44 in the integration profile (`-Ddocker.api.version=...` overrides it).

## Pending

Vue UI, durable run history, scheduling, historical social-row migration and public deployment review. The current single administrator is configured by environment; persistent user management is future work. The repositories stay private. AI-Ready assets and generated `/docs/` are excluded from Git.
