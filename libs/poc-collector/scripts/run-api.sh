#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/dependency-classpath.txt
classpath="$(cat target/dependency-classpath.txt)"
exec java -cp "target/classes:$classpath" com.henrique.collector.CollectorApiServer
