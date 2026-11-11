#!/usr/bin/env bash
# Deploys poc-dashboard (API + web) to Railway for one environment.
# Usage: scripts/railway-deploy.sh develop|production
set -euo pipefail

target="${1:-}"
case "$target" in
  develop)    expected_branch=develop ;;
  production) expected_branch=main ;;
  *) echo "Usage: $0 develop|production" >&2; exit 2 ;;
esac

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

command -v railway >/dev/null || { echo "Railway CLI missing. Install: brew install railway" >&2; exit 1; }
command -v mvn >/dev/null || { echo "Maven missing" >&2; exit 1; }
command -v npm >/dev/null || { echo "npm missing" >&2; exit 1; }

branch="$(git rev-parse --abbrev-ref HEAD)"
if [ "$branch" != "$expected_branch" ]; then
  echo "Refusing to deploy '$target' from branch '$branch' (expected '$expected_branch')." >&2
  exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo "Working tree is not clean; commit or stash before deploying." >&2
  exit 1
fi

if [ "$target" = production ]; then
  printf 'Deploy to PRODUCTION from %s? Type "production" to continue: ' "$branch"
  read -r answer
  [ "$answer" = production ] || { echo "Cancelled." >&2; exit 1; }
fi

echo "==> Backend tests"
mvn -B -q test
echo "==> Frontend tests and build"
(cd frontend && npm ci --silent && npm test --silent && npm run build --silent)

api_service="${RAILWAY_API_SERVICE:-poc-dashboard-api}"
web_service="${RAILWAY_WEB_SERVICE:-poc-dashboard-web}"
env_name="$target"

echo "==> Uploading API ($api_service) to Railway environment '$env_name'"
railway up --detach --environment "$env_name" --service "$api_service"
echo "==> Uploading web ($web_service) to Railway environment '$env_name'"
railway up --detach --environment "$env_name" --service "$web_service" frontend
echo "Deploy requested for '$target'. Check status with: railway status --environment $env_name"
