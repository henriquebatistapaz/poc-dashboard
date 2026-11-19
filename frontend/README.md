# POC Dashboard UI

Private Vue 3 + TypeScript + Vite panel, using vue-i18n and the existing Dashboard/runner API. No new collector implementation is added to the frontend.

## Local development

Start the backend with the credentials described in the repository README. Its default Compose API port is 8084.

```sh
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/v1`, Swagger and OpenAPI to `http://127.0.0.1:8084`. If running Spring Boot directly on port 8080, use `API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev`. Set `API_PROXY_TARGET` when starting Vite to use another local backend. The browser never receives a configurable arbitrary API host or a backend secret.

## Features

Login, logout and session expiration; RSS/Atom paste-and-collect with verified feed examples; keyword search for Google News, WordPress, Mastodon, Lemmy, DEV.to, Hacker News, Stack Exchange, GitHub, TabNews, Wikipedia, GitLab and Lobsters; synthetic Alpha/Beta fixture JSON examples; new/duplicate counters; recent asynchronous run history with resume tracking for pending runs; source/title/collection-date filters; pagination; native accessible detail dialog; safe original links; responsive mobile layout and localized empty/loading/error states. Alpha/Beta inputs are synthetic fixture JSON, not live social networks.

The JWT stays in memory for its 15-minute session and is cleared on logout/401/expiration. Reloading requires login again. Requests carry Bearer authorization, never query credentials. Only non-sensitive browser preferences (`coleta.locale` and `poc-dashboard:theme`) are stored locally. Upstream error bodies are not displayed. Titles are rendered as text, and only HTTP(S) links without embedded credentials can be opened.

## Languages

Catalogs live in `src/locales/pt-BR.json`, `en.json` and `es.json`. Portuguese is the fallback; first visit follows supported browser English/Spanish, otherwise Portuguese. An explicit language choice takes precedence. Native names are shown in the picker. Date/number formatting uses the active locale and the user's local time zone. Dates sent to the backend are converted to UTC instants.

Add each new message key to every catalog. Translate labels, accessible names, validation and errors, not just headings. Use vue-i18n interpolation rather than concatenating translated phrases. Escape literal message syntax where necessary (for example `{'@'}` in email placeholders). Source identifiers and API payloads stay unchanged across languages. Catalog parity and live switching are tested.

## Checks

```sh
npm test
npm run build
# First time, install an isolated browser:
npx playwright install chromium
npm run test:e2e
```

Unit tests cover locale parity/preference, safe links, API behavior, cancellable run polling, run history and collection input/results. Deterministic browser tests cover the complete UI with fixture API responses, all languages, filters, failed login/session expiration, duplicate-only results, recent run history and mobile overflow. They start Vite automatically. On a machine with Chrome, `CHROME_PATH` can select its executable instead of downloading Chromium.

An optional live test uses the running Compose frontend, real login/API/PostgreSQL and a public XKCD Atom feed. Create an ignored credentials JSON file outside Git with `email` and `password`, then run:

```sh
UI_BASE_URL=http://localhost:8085 LIVE_CREDENTIALS_FILE=/absolute/path/to/local-credentials.json npm run test:e2e
```

External source availability is not deterministic; the live test is skipped unless explicitly configured. Browser screenshots/traces stay in ignored `test-results/`. Never commit credentials, tokens, private feed URLs or test traces containing them.

## Compose

The root Compose file builds this directory with Node 24 and serves the resulting assets through Nginx on localhost:8085. Nginx proxies API calls to the backend service; API reads are not cached, and long collection requests allow up to 120 seconds. No frontend code embeds backend credentials. The UI is private/local by default; public deployment is a separate task.
