# Multi-Source Collector Mini

Private Java 21 / Maven command-line collector for public RSS/Atom links and approved Reddit Data API results. It reads Google News search RSS, a user-provided Google Alerts RSS URL, public WordPress RSS/Atom feeds, and Reddit posts through official OAuth. Every returned item contains only source, stable ID, title, link, and timestamps.

## Build and tests

```bash
mvn test
mvn package
```

## Run

```bash
java -jar target/collector-mini-0.1.0-SNAPSHOT.jar google-news "open data Brazil"
```

For Google Alerts, create and configure an alert in the Google account first, then provide its RSS feed URL through the environment. The URL is a secret and must not be stored in shell history, Git, logs, or screenshots.

```bash
read -s GOOGLE_ALERTS_FEED_URL
export GOOGLE_ALERTS_FEED_URL
java -jar target/collector-mini-0.1.0-SNAPSHOT.jar google-alerts
unset GOOGLE_ALERTS_FEED_URL
```

For a public WordPress feed:

```bash
export WORDPRESS_FEED_URL='https://wordpress.org/news/feed/'
java -jar target/collector-mini-0.1.0-SNAPSHOT.jar wordpress
unset WORDPRESS_FEED_URL
```

For Reddit, first request approval for the external Data API integration from Reddit and register an OAuth app. The `RedditOAuthAuthorization` helper builds a user-consent URL with a cryptographically random state, `read`-only scope, and permanent access, and exchanges the returned authorization code for tokens only after constant-time state validation. Configure `REDDIT_API_ACCESS_APPROVED=true`, `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET`, `REDDIT_REFRESH_TOKEN`, and your Reddit username in `REDDIT_USERNAME` in the local environment/secret store. The collector refreshes access tokens in memory, sends a truthful descriptive User-Agent, redacts token strings, and does not log credentials or tokens. Keep `REDDIT_API_ACCESS_APPROVED=false` until Reddit approves the app/use case.

```bash
java -jar target/collector-mini-0.1.0-SNAPSHOT.jar reddit java
```

The program prints a JSON array of normalized feed items to stdout. Google Alerts fetch/parse errors omit the secret URL. Feed requests have timeouts, a 1 MB response cap, and redirects disabled. WordPress URLs must use HTTPS and resolve only to public IP addresses.

## Source status

- **Google News:** implemented using its observed search RSS URL shape. Google does not provide an official API contract for this endpoint, so behavior may change.
- **Google Alerts:** implemented only for a user-supplied feed URL if the user's account exposes one. Current Google help describes email alerts but does not document RSS delivery; no account feed URL is included in this repository.
- **WordPress:** implemented using public RSS or Atom feeds supplied by the user.
- **Reddit:** official OAuth/Data API adapter; disabled until Reddit access approval and valid local credentials are configured. No HTML scraping.
- **Issuu:** unavailable until an official permitted tokenless feed/API contract is verified.


## Integration planning

This collector remains a standalone source adapter. A future integration with `rss-collector-mini` and `collector-runner-mini` is being explored in the private `dashboard-mini` repository; its contract and architecture are still undecided.
