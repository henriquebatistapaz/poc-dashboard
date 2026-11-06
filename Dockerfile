# syntax=docker/dockerfile:1.7
# Build autocontido: as bibliotecas privadas (collector-mini, rss-collector-mini,
# collector-runner-mini) ficam em libs/ (atualize com scripts/sync-libs.sh). Nao precisa de token.
FROM maven:3.9-eclipse-temurin-21 AS build
COPY libs /opt/libs
RUN \
    for repo in collector-mini rss-collector-mini collector-runner-mini; do \
      mvn -B -q -f "/opt/libs/${repo}/pom.xml" -DskipTests install ; \
    done
WORKDIR /app
COPY pom.xml /app/pom.xml
COPY src /app/src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/dashboard-mini-0.1.0-SNAPSHOT.jar app.jar
# No container a API escuta em todas as interfaces (IPv4 e IPv6, rede privada do Railway).
ENV SERVER_ADDRESS=::
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
