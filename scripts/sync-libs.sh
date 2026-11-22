#!/usr/bin/env bash
# Atualiza libs/ com o codigo das bibliotecas privadas (branch main por padrao).
# Uso: scripts/sync-libs.sh [branch]
set -euo pipefail
branch="${1:-main}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
for repo in poc-collector poc-rss-collector poc-collector-runner poc-collector-oauth; do
  gh repo clone "henriquebatistapaz/${repo}" "$tmp/$repo" -- -q --depth 1 -b "$branch"
  rm -rf "$root/libs/$repo" && mkdir -p "$root/libs/$repo"
  (cd "$tmp/$repo" && git archive HEAD) | tar -x -C "$root/libs/$repo"
  echo "$repo $(git -C "$tmp/$repo" rev-parse --short HEAD)"
done
