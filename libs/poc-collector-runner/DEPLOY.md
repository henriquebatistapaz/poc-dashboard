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

## Variáveis de retenção e takedown

- `SERVER_ADDRESS` usa `127.0.0.1` por padrão; mantenha a API standalone em loopback.
- `RUNNER_ADMIN_API_KEY` habilita o `DELETE /v1/posts/{source}/{externalId}`; sem valor, a operação retorna 403.
- `POST_RETENTION_DAYS` habilita a remoção agendada; padrão `0` mantém retenção automática desativada. Configure um período aprovado para o ambiente antes de ativá-la.

## Pendências de publicação

Provisionar PostgreSQL e definir `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. O build remoto precisa acessar os dois artefatos Maven ou compilar os projetos irmãos antes. Ver o [README](README.md) para o teste com PostgreSQL.
