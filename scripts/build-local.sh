#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
collector_repo=${COLLECTOR_REPO:-"$project_dir/../poc-collector"}
rss_repo=${RSS_REPO:-"$project_dir/../poc-rss-collector"}
runner_repo=${RUNNER_REPO:-"$project_dir/../poc-collector-runner"}
for repo_dir in "$collector_repo" "$rss_repo" "$runner_repo"; do
    if [ ! -f "$repo_dir/pom.xml" ]; then
        printf 'Missing Maven project: %s\n' "$repo_dir" >&2
        exit 1
    fi
done
"$project_dir/mvnw" -f "$collector_repo/pom.xml" install
"$project_dir/mvnw" -f "$rss_repo/pom.xml" install
"$project_dir/mvnw" -f "$runner_repo/pom.xml" clean install
"$project_dir/mvnw" -f "$project_dir/pom.xml" clean package
(
    cd "$project_dir/frontend"
    npm ci
    npm test
    npm run build
)
