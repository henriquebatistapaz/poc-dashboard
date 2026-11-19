# Multi-Source Collector Mini

Private Java 27 / Maven 3.9 command-line collector for public RSS/Atom links. It reads Google News search RSS and public WordPress.com tag RSS feeds. Every returned item contains only source, stable ID, title, link, and timestamps. The collector compiles and runs on Java 27.

## Build and tests

```bash
mvn test
mvn package
```

## Run

```bash
java -jar target/poc-collector-0.1.0-SNAPSHOT.jar google-news "open data Brazil"
```

For public WordPress.com tag search:

```bash
java -jar target/poc-collector-0.1.0-SNAPSHOT.jar wordpress java
```

The program prints a JSON array of normalized feed items to stdout. Feed requests have timeouts, a 1 MB response cap, and redirects disabled. Public HTTPS feeds reject private network destinations.

## REST API, schedules, and persistence

The standalone API stores normalized items and encrypted scheduled-feed inputs in a local JSON data file. It runs as a single instance; set `COLLECTOR_DATA_FILE` to a persistent mounted volume when running in a container. Item retention defaults to 30 days and can be changed with `COLLECTOR_RETENTION_DAYS` (1–3650). Scheduled intervals are limited to 5 minutes–24 hours.

Set separate random values for the bearer API key and AES-GCM encryption key. The API key must be at least 32 bytes; the encryption key must be 32 bytes encoded as 64 hexadecimal characters.

```bash
export COLLECTOR_API_KEY="$(openssl rand -hex 32)"
export COLLECTOR_SECRET_KEY="$(openssl rand -hex 32)"
export COLLECTOR_DATA_FILE="$PWD/collector-data.json"
export COLLECTOR_RETENTION_DAYS=30
./scripts/run-api.sh
```

The API binds to `127.0.0.1:8090` by default. Keep that default for local use. For a remote deployment, place it behind a TLS-terminating reverse proxy before binding to a network interface. `GET /health` and `GET /openapi.json` are public; `/v1/*` requires `Authorization: Bearer $COLLECTOR_API_KEY`.

- `GET /v1/sources` — capability status for each source.
- `POST /v1/collections` — collect once with `{"source":"WORDPRESS","input":"java"}`; returned items are deduplicated and stored.
- `GET /v1/items?limit=100&source=WORDPRESS` — list stored items (limit 1–500).
- `GET /v1/feeds` and `POST /v1/feeds` — list or schedule a collection. Create with `{"source":"WORDPRESS","input":"java","intervalSeconds":3600}`.
- `DELETE /v1/feeds/{id}` — remove a schedule.

Scheduled source inputs are encrypted at rest. Losing or changing `COLLECTOR_SECRET_KEY` makes existing schedules unreadable; restore the same key with the data file. Issuu public discovery is unavailable and stays disabled.

## Source status

- **Google News:** implemented using its observed search RSS URL shape. Google does not provide an official API contract for this endpoint, so behavior may change.
- **WordPress:** implemented via public WordPress.com tag RSS feeds (`https://wordpress.com/tag/<slug>/feed/`).
- **Issuu:** unavailable for public discovery. Issuu's first-party [API SDK](https://github.com/issuu/issuu-api-sdk) uses an account token for authenticated account/publication operations; it does not document an unauthenticated public search/feed API. No scraping or hidden endpoints are used.


## Integration planning

This collector remains a standalone source adapter. A future integration with `poc-rss-collector` and `poc-collector-runner` is being explored in the private `poc-dashboard` repository; its contract and architecture are still undecided.
