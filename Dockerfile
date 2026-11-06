# syntax=docker/dockerfile:1.7
# Build is self-contained: the collector libraries are private GitHub repositories compiled from
# source. The token is a build argument used ONLY in this intermediate stage; the runtime image
# is built from a fresh stage and does not contain the token or the cloned sources.
#   docker build --build-arg GITHUB_TOKEN=$(gh auth token) -t dashboard-mini:local .
FROM maven:3.9-eclipse-temurin-21 AS build
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/libs
ARG GITHUB_TOKEN
ARG LIBRARY_BRANCH=main
RUN test -n "${GITHUB_TOKEN}" || { echo "GITHUB_TOKEN build argument is required" >&2; exit 1; } && \
    for repo in collector-mini rss-collector-mini collector-runner-mini; do \
      git clone --depth 1 --branch "${LIBRARY_BRANCH}" \
        "https://x-access-token:${GITHUB_TOKEN}@github.com/henriquebatistapaz/${repo}.git" "/opt/libs/${repo}" && \
      rm -rf "/opt/libs/${repo}/.git" ; \
    done
RUN for repo in collector-mini rss-collector-mini collector-runner-mini; do \
      mvn -B -q -f "/opt/libs/${repo}/pom.xml" -DskipTests install ; \
    done
WORKDIR /app
COPY pom.xml /app/pom.xml
COPY src /app/src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/dashboard-mini-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
