# Dashboard Mini — Especificação inicial

**Status:** Target specification with a partial backend implementation. The sections below describe the full intended phase, not completed functionality.

## Implementation status (reviewed)

Implemented: Java/Maven Spring backend; Lombok entity/constructor boilerplate; PostgreSQL/Flyway canonical item schema; paginated/filterable read API; read-only API key; runtime administrator with BCrypt password hash and 15-minute issuer/signature/expiry-validated JWT; authenticated synchronous manual collection through the embedded runner; source/RSS/social library integration; Swagger and local Docker Compose. The runner does not own canonical migrations. Standalone runner configuration is isolated from dashboard configuration.

The authenticated collection path covers RSS 2.0/Atom 1.0, Google News, user-owned Google Alerts feeds, WordPress and synthetic Alpha/Beta adapters. Reddit still requires official approval and runtime OAuth credentials; Issuu remains unavailable. Collection inputs are not logged or stored. PostgreSQL tests exercise actual Flyway/HTTP/runner/parser behavior with fixture upstream fetches, including duplicate collection and queries. See README for reproducible commands and detailed limits.

Pending: Vue frontend, durable run history/scheduling, persistent user administration and historical social-row migration. The single runtime administrator uses `DASHBOARD_ADMIN_EMAIL` and `DASHBOARD_ADMIN_PASSWORD_HASH`; no `app_user` table or registration is implemented. These runtime values replace the need to seed a user row in this slice. Public deployment remains pending review.

**Repositório:** Privado
**Fase desta spec:** 1 — backend (Spring Boot, PostgreSQL, Swagger, JWT) e painel Vue separado, com coleta sob demanda.

## 1. Objetivo

Um sistema com frontend e backend que lê os itens coletados pelo `collector-runner-mini` no banco e permite:

- consultar os itens coletados, com filtros por fonte, palavra-chave e período;
- disparar uma coleta sob demanda pelo `collector-runner-mini`.

Coletas agendadas ficam fora desta fase. Alertas de novidade também.

## 2. Relação com os outros projetos

| Projeto | Papel |
|---|---|
| `collector-mini` | Coleta Google News, Google Alerts, WordPress e Reddit. Biblioteca Maven. |
| `rss-collector-mini` | Valida e lê feeds RSS/Atom. Biblioteca Maven. |
| `collector-runner-mini` | Chama os coletores, normaliza, deduplica e grava os itens no banco. Biblioteca Maven. |
| `dashboard-mini` (este) | Dono do banco e das migrações. Expõe a API, dispara coletas pelo `collector-runner-mini` e serve o painel. |

**Integração por biblioteca:** o hub declara o `collector-runner-mini` como dependência Maven e chama os serviços dele dentro do mesmo processo. Não há chamada HTTP entre os projetos nesta fase.

**Dono do banco:** o `dashboard-mini` cria e versiona todas as tabelas com Flyway, incluindo a dos itens coletados. O `collector-runner-mini` só grava nessas tabelas e não tem migrações próprias.

## 3. Requisitos funcionais

- **RF1 — Listar itens:** `GET /v1/items` com paginação e filtros por fonte, palavra-chave (texto do título) e intervalo de datas.
- **RF2 — Detalhar item:** `GET /v1/items/{id}` com fonte, id externo, título, link original e datas.
- **RF3 — Disparar coleta:** `POST /v1/collections` recebe fonte e entrada (palavra-chave, URL ou subreddit) e chama o `collector-runner-mini`. Devolve quantos itens foram gravados e quantos eram novos.
- **RF4 — Login:** `POST /v1/auth/login` com e-mail e senha. Devolve um JWT. Um usuário administrador inicial é criado por variável de ambiente.
- **RF5 — Acesso por API key:** `GET /v1/items` aceita o header `X-API-Key`, configurado por variável de ambiente, para consumo por sistemas externos.
- **RF6 — Painel Vue:** telas de login, lista de itens com filtros, detalhe do item e disparo manual de coleta.
- **RF7 — Documentação:** Swagger/OpenAPI em `/swagger-ui.html` e `/v3/api-docs`.

## 4. Requisitos não funcionais

- Java 21, Spring Boot 3.4.x, Maven, JUnit 5.
- PostgreSQL como banco; Flyway para todas as tabelas.
- Frontend em Vue 3, em `frontend/` dentro deste repositório.
- Segredos (senha do admin, chave de API, segredo do JWT, credenciais do banco) só por variável de ambiente. Nada no Git.
- Respostas paginadas, com no máximo 100 itens por página.
- Coletas usam a mesma regra de deduplicação do `collector-runner-mini`.
- O hub não contorna bloqueios de fonte: fontes desabilitadas nos coletores continuam desabilitadas.

## 5. Modelo de dados

Criado pelo hub, com Flyway:
- `collected_item`: id interno, fonte, id externo (único por fonte), título, link original, data de publicação e data da coleta.
- `app_user`: planned persistent user administration only; this slice uses one runtime administrator and creates no user table.

O `collector-runner-mini` grava em `collected_item` usando o mesmo esquema, sem criar tabelas. Its legacy standalone `social_posts` API remains independent; historical rows are not migrated.

## 6. Autenticação

- Painel e rotas de escrita (`POST /v1/collections`): JWT emitido no login, com expiração curta.
- Consumo externo de leitura (`GET /v1/items`): JWT ou `X-API-Key`.
- A comparação da chave de API é feita em tempo constante.
- Senhas guardadas só como hash forte.

## 6.1 Retenção

- Os itens coletados são guardados **sempre**, sem exclusão automática por tempo.
- A única exclusão prevista é a remoção de um item sob pedido de remoção (por exemplo, de uma fonte ou de quem tem direito sobre o conteúdo), registrada como exceção manual.

## 7. Implantação

- **Fase 1:** Docker Compose local com PostgreSQL, backend e frontend.
- **Depois:** deploy no Railway. A autenticação precisa estar ativa antes de qualquer deploy público; esse é um bloqueio registrado nesta spec.

## 8. Fora de escopo desta fase

- Coletas agendadas.
- Alertas de novidade (e-mail, Telegram).
- Multiusuário com papéis além de administrador.
- Chamadas HTTP entre os projetos.
- Mensageria e fila.
- Coleta de fontes ainda não permitidas (Issuu, Reddit sem aprovação).

## 9. Critérios de aceite

- Listar, filtrar e detalhar itens funciona com dados gravados pelo `collector-runner-mini`.
- Uma coleta disparada pela API grava itens no banco e devolve a contagem.
- Sem JWT ou API key válidos, as rotas protegidas respondem 401.
- Painel Vue faz login, lista itens com filtros e dispara uma coleta.
- Swagger publicado e acessível.
- Testes passam com `mvn test`, e `npm run build` passa no frontend.

## 10. Perguntas em aberto

1. O painel terá cadastro de usuários pela interface, ou só o administrador inicial criado por variável de ambiente? Esta spec assume só o administrador inicial.

## 11. Fases

- **Fase 1 (esta spec):** backend com API, JWT, Swagger e migrações; painel Vue; Docker Compose local.
- **Fase 2:** deploy no Railway, depois de revisar a autenticação e as perguntas em aberto.
