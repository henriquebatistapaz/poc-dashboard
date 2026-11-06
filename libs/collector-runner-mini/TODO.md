# TODO

- [x] Define two synthetic JSON fixture formats and provenance-free examples.
- [x] Implement adapters that map fixtures into the canonical post model.
- [x] Validate required fields and ISO-8601 publication timestamps.
- [x] Persist posts and enforce duplicate identity `(source, externalId)` with Spring Data JPA and PostgreSQL.
- [x] Add `POST /v1/posts` ingestion and `GET /v1/posts` listing.
- [x] Add local PostgreSQL Compose configuration and H2-backed JUnit tests.
- [x] Expose both API operations in OpenAPI and Swagger UI.
- [ ] Add pagination and filters after approving query semantics.
- [ ] Define retention, deletion/takedown, and privacy behavior before production use.
- [ ] Connect fixture adapters to a controlled ingestion route after approving its contract.
- [ ] Consider ActiveMQ Classic only if asynchronous job delivery is needed; consider Kafka only for replay or multiple independent consumers.
