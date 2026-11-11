# Deploy e execução local

Biblioteca Maven e API Spring Boot que grava itens coletados no PostgreSQL.

## Validação local

Use JDK 27.0.x e Maven 3.9.16. Execute os comandos a partir da raiz do repositório.

```sh
mvn -f ../poc-collector/pom.xml clean install
mvn -f ../poc-rss-collector/pom.xml clean install
docker compose up -d postgres
mvn clean verify
mvn spring-boot:run
```

## Pendências de publicação

Provisionar PostgreSQL e definir `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. O build remoto precisa acessar os dois artefatos Maven ou compilar os projetos irmãos antes. Ver o [README](README.md) para o teste com PostgreSQL.
