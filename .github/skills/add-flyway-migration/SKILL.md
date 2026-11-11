---
name: add-flyway-migration
description: Criar uma migration Flyway nova no poc-dashboard sem quebrar o histórico de schema.
---

# Nova migration Flyway

1. Crie `src/main/resources/db/migration/V<próximo>__<descricao>.sql` (hoje existem V1..V3). Nunca edite uma migration já aplicada.
2. Ajuste a entidade em `domain/` e o repository; mantenha o H2 dos testes compatível ou cubra no perfil `postgres-it`.
3. Rode `mvn test` e `mvn -Ppostgres-it verify`.
4. Documente a tabela/coluna nova em SPEC.md (modelo de dados).
