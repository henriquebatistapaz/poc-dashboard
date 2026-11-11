# Multi-Source Collector Mini

Private Java 27 / Maven 3.9 command-line collector for public RSS/Atom links and approved Reddit Data API results. It reads Google News search RSS, a user-provided Google Alerts RSS URL, public WordPress RSS/Atom feeds, and Reddit posts through official OAuth. Every returned item contains only source, stable ID, title, link, and timestamps. The collector compiles and runs on Java 27.

## Build and tests

```bash
mvn test
mvn package
```

## Run

```bash
java -jar target/poc-collector-0.1.0-SNAPSHOT.jar google-news "open data Brazil"
```

For Google Alerts, create and configure an alert in the Google account first, then provide its RSS feed URL through the environment. The URL is a secret and must not be stored in shell history, Git, logs, or screenshots.

```bash
read -s GOOGLE_ALERTS_FEED_URL
export GOOGLE_ALERTS_FEED_URL
java -jar target/poc-collector-0.1.0-SNAPSHOT.jar google-alerts
unset GOOGLE_ALERTS_FEED_URL
```

For a public WordPress feed:

```bash
export WORDPRESS_FEED_URL='https://wordpress.org/news/feed/'
java -jar target/poc-collector-0.1.0-SNAPSHOT.jar wordpress
unset WORDPRESS_FEED_URL
```

For Reddit, first request approval for the external Data API integration from Reddit and register an OAuth app. The `RedditOAuthAuthorization` helper builds a user-consent URL with a cryptographically random state, `read`-only scope, and permanent access, and exchanges the returned authorization code for tokens only after constant-time state validation. Configure `REDDIT_API_ACCESS_APPROVED=true`, `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET`, `REDDIT_REFRESH_TOKEN`, and your Reddit username in `REDDIT_USERNAME` in the local environment/secret store. The collector refreshes access tokens in memory, sends a truthful descriptive User-Agent, redacts token strings, and does not log credentials or tokens. Keep `REDDIT_API_ACCESS_APPROVED=false` until Reddit approves the app/use case.

```bash
java -jar target/poc-collector-0.1.0-SNAPSHOT.jar reddit java
```

The program prints a JSON array of normalized feed items to stdout. Google Alerts fetch/parse errors omit the secret URL. Feed requests have timeouts, a 1 MB response cap, and redirects disabled. WordPress URLs must use HTTPS and resolve only to public IP addresses.

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
- `POST /v1/collections` — collect once with `{"source":"WORDPRESS","input":"https://example.org/feed/"}`; returned items are deduplicated and stored.
- `GET /v1/items?limit=100&source=WORDPRESS` — list stored items (limit 1–500).
- `GET /v1/feeds` and `POST /v1/feeds` — list or schedule a collection. Create with `{"source":"WORDPRESS","input":"https://example.org/feed/","intervalSeconds":3600}`.
- `DELETE /v1/feeds/{id}` — remove a schedule.

Scheduled source inputs are encrypted at rest. Losing or changing `COLLECTOR_SECRET_KEY` makes existing schedules unreadable; restore the same key with the data file. The Reddit connector remains disabled until Reddit grants external Data API approval and valid OAuth credentials are configured. Issuu public discovery is unavailable and stays disabled.

## Source status

- **Google News:** implemented using its observed search RSS URL shape. Google does not provide an official API contract for this endpoint, so behavior may change.
- **Google Alerts:** implemented only for a user-supplied feed URL if the user's account exposes one. Current Google help describes email alerts but does not document RSS delivery; no account feed URL is included in this repository.
- **WordPress:** implemented using public RSS or Atom feeds supplied by the user.
- **Reddit:** official OAuth/Data API adapter; disabled until Reddit access approval and valid local credentials are configured. No HTML scraping.
- **Issuu:** unavailable for public discovery. Issuu's first-party [API SDK](https://github.com/issuu/issuu-api-sdk) uses an account token for authenticated account/publication operations; it does not document an unauthenticated public search/feed API. No scraping or hidden endpoints are used.


## Integration planning

This collector remains a standalone source adapter. A future integration with `poc-rss-collector` and `poc-collector-runner` is being explored in the private `poc-dashboard` repository; its contract and architecture are still undecided.
