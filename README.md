# Dashboard Mini

Private Java 21 / Maven backend for querying collected items. Spring Boot, Spring Data JPA, PostgreSQL, Flyway and Swagger/OpenAPI are included.

## Implemented

- `GET /v1/items`: filter by source, case-insensitive title (`q`) and collection dates (`from`, `to`, ISO instants). Pages start at zero; size defaults to 20 and is clamped to 1–100. Results sort by collection time and ID descending.
- `GET /v1/items/{id}`: item details, or 404.
- Read endpoints require `X-API-Key`. Configure `DASHBOARD_API_KEY` at runtime. Missing, incorrect or unconfigured keys return 401. No key is stored in Git.
- PostgreSQL schema managed by Flyway. This is an initial read API; it has not yet been integrated with collector-runner-mini.
- Public API documentation at `/swagger-ui.html` and `/v3/api-docs`; Swagger has an Authorize button for the key.

## Run locally

Start a PostgreSQL database, then set `DB_URL`, `DB_USER`, `DB_PASSWORD` and `DASHBOARD_API_KEY` in your shell. Defaults for the database are local development values only (`jdbc:postgresql://localhost:5432/dashboard`, user/password `dashboard`). Set a nonempty API key before querying items.

```sh
mvn spring-boot:run
```

Open Swagger at `http://localhost:8080/swagger-ui.html`. The database initially contains no items. Tests use H2 and require no external database:

```sh
mvn test
```

## Planned

See [SPEC.md](SPEC.md) for the target architecture. JWT login, admin provisioning, Vue UI, manual collection, collector-runner integration and Compose are pending. API keys grant read access only. Keep this prototype local until the complete authentication and deployment review is approved.

Related private projects: `rss-collector-mini` (RSS/Atom), `collector-mini` (source collectors) and `collector-runner-mini` (normalization and persistence).

AI-Ready assets and generated `/docs/` remain local and ignored by Git.
