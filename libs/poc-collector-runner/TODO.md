# TODO

- [x] Define two synthetic JSON fixture formats and provenance-free examples.
- [x] Implement adapters that map fixtures into the canonical post model.
- [x] Validate required fields and ISO-8601 publication timestamps.
- [x] Persist posts and enforce duplicate identity `(source, externalId)` with Spring Data JPA and PostgreSQL.
- [x] Add `POST /v1/posts` ingestion and `GET /v1/posts` listing.
- [x] Add local PostgreSQL Compose configuration and H2-backed JUnit tests.
- [x] Expose both API operations in OpenAPI and Swagger UI.
- [x] Add backwards-compatible pagination/search at `GET /v1/posts/search`: source, text/author, publication date range; page 0/size 20 defaults and size 100 maximum.
- [x] Define opt-in retention (`POST_RETENTION_DAYS`, default 0) and keyed takedown by source/external ID; bind the standalone API to loopback by default.
- [x] Keep fixture adapters behind the existing dashboard `POST /v1/collections` contract; do not add a duplicate fixture-only runner route.
- [ ] Consider ActiveMQ Classic only if asynchronous job delivery is needed; consider Kafka only for replay or multiple independent consumers.
