# syntax=docker/dockerfile:1.7
# Build is self-contained: the collector libraries are private GitHub repositories and are
# compiled from source during the image build. Pass the token as a BuildKit secret, never as ARG:
#   docker build --secret id=github_token,env=GITHUB_TOKEN .
FROM maven:3.9-eclipse-temurin-21 AS build
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/libs
ARG LIBRARY_BRANCH=main
RUN --mount=type=secret,id=github_token \
    export TOKEN="$(cat /run/secrets/github_token)" && \
    for repo in collector-mini rss-collector-mini collector-runner-mini; do \
      git clone --depth 1 --branch "${LIBRARY_BRANCH}" \
        "https://x-access-token:${TOKEN}@github.com/henriquebatistapaz/${repo}.git" "/opt/libs/${repo}" ; \
    done && \
    unset TOKEN
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
