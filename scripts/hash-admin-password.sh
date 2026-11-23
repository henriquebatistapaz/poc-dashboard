#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
./mvnw -q compile dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath
exec java -cp "target/classes:$(cat target/runtime-classpath)" dev.henrique.dashboard.security.PasswordHashCli
