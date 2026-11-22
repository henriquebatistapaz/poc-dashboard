# syntax=docker/dockerfile:1.7
# Build autocontido: as bibliotecas privadas (poc-collector, poc-rss-collector,
# poc-collector-runner, poc-collector-oauth) ficam em libs/ (atualize com scripts/sync-libs.sh). Nao precisa de token.
FROM maven:3.10.0-eclipse-temurin-27 AS build
COPY libs /opt/libs
RUN \
    for repo in poc-collector poc-rss-collector poc-collector-runner poc-collector-oauth; do \
      mvn -B -q -f "/opt/libs/${repo}/pom.xml" -DskipTests install || exit 1 ; \
    done
WORKDIR /app
COPY pom.xml /app/pom.xml
COPY src /app/src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:27-jre
WORKDIR /app
COPY --from=build /app/target/poc-dashboard-0.1.0-SNAPSHOT.jar app.jar
# No container a API escuta em todas as interfaces (IPv4 e IPv6, rede privada do Railway).
ENV SERVER_ADDRESS=::
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
