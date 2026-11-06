# Dashboard Mini

Private discovery project for a future integration between three existing private Java projects:

- `rss-collector-mini` — validates and parses RSS 2.0 and Atom 1.0 feeds.
- `collector-runner-mini` — normalizes, deduplicates, and persists social posts.
- `collector-mini` — collects Google News, user-supplied Google Alerts feeds, WordPress feeds, and Reddit through official OAuth/Data API access when approval and credentials are configured.

## Current status

This repository contains a draft specification and no integration code. The first proposed slice is local and private: validate a feed, start a manual run, and query normalized results. Runtime technology, API details, UI, persistence ownership, and library-versus-HTTP integration remain open decisions. See [SPEC.md](SPEC.md) for scope, acceptance criteria, source availability, and decisions required before implementation.

