# Dashboard Mini — Especificação inicial

**Status:** Target specification with a partial backend implementation. The sections below describe the full intended phase, not completed functionality.

## Implementation status (reviewed)

Implemented: Java/Maven Spring backend with Lombok; PostgreSQL schema managed by Flyway (`collected_item` V1, `collection_run` V2); paginated and filterable read API capped at 100 items per page; read-only API key for item reads; administrator from environment with BCrypt hash and JWT (15 minutes); asynchronous collection runs (`POST /v1/collections` returns `202` and is polled through `GET /v1/collections/{id}`); run history; startup recovery of interrupted runs; Swagger; local Docker Compose. Vue/TypeScript panel with login, collection, filters, pagination, details, recent asynchronous run history and resume tracking, plus Portuguese, English and Spanish localization. Current verification: `mvn test` — 39 backend tests on H2; `npm test` — 15 panel tests; Playwright — 6 deterministic browser tests passed. The optional Testcontainers PostgreSQL integration profile and live Compose browser test are separate.

The authenticated collection path covers RSS 2.0/Atom 1.0, Google News, user-owned Google Alerts feeds, WordPress and synthetic Alpha/Beta adapters. Reddit still requires official approval and runtime OAuth credentials; Issuu remains unavailable. Collection inputs are not logged or stored. PostgreSQL tests exercise actual Flyway/HTTP/runner/parser behavior with fixture upstream fetches, including duplicate collection and queries. See README for reproducible commands and detailed limits.

Pending: scheduling, persistent user administration and historical social-row migration. The single runtime administrator uses `DASHBOARD_ADMIN_EMAIL` and `DASHBOARD_ADMIN_PASSWORD_HASH`; no `app_user` table or registration is implemented. These runtime values replace the need to seed a user row in this slice. Public deployment remains pending review.

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
- **RF3 — Disparar coleta:** `POST /v1/collections` recebe fonte e entrada (palavra-chave, URL ou subreddit), grava uma execução com status `PENDING` e responde `202 Accepted` com o `Location` da execução. A coleta roda em segundo plano pelo `collector-runner-mini`. `GET /v1/collections/{id}` mostra o status (`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`) e a contagem de buscados, inseridos e duplicados. `GET /v1/collections` lista as execuções mais recentes.
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

## 5.1 Execução de coletas

- Cada pedido de coleta vira uma linha em `collection_run` (migração V2), com origem, status, datas, contagens e uma mensagem de erro curta.
- A entrada informada (palavra-chave, URL ou subreddit) **não é guardada**. Ela existe só em memória durante a execução, porque pode conter segredo (por exemplo, a URL de um Google Alerts).
- Falhas são traduzidas para mensagens fixas. Nenhum texto vindo da fonte chega à resposta da API.
- Execuções que ficaram `PENDING` ou `RUNNING` quando a aplicação reiniciou são marcadas como `FAILED` com o motivo "Interrupted by application restart".
- O pool de execução tem 2 threads. Não há fila persistida nem broker nesta fase. Se for preciso agendamento ou worker separado, a tabela `collection_run` já serve como fila.

## 6. Autenticação

Implementada no `develop` (PR #6). Esta seção descreve o estado atual.

- **Login:** `POST /v1/auth/login` com e-mail e senha devolve um JWT (Bearer), válido por 15 minutos, com escopos `items:read` e `collections:write`. O administrador inicial é criado a partir de variáveis de ambiente.
- **Leitura de itens:** `GET /v1/items` e `GET /v1/items/{id}` aceitam JWT com escopo `items:read` ou o header `X-API-Key`, que concede o papel `ROLE_API_READER`.
- **Disparo de coleta:** `POST /v1/collections` exige JWT com escopo `collections:write`. A chave de API não serve para essa rota. O painel mantém as dez execuções recentes e permite acompanhar novamente uma execução pendente após o login.
- **Demais rotas:** negadas por padrão. Sem credencial válida, a resposta é 401.
- **Chave de API:** comparada em tempo constante.
- **Senhas:** guardadas apenas como hash.
- **Pendências:** cadastro de usuários além do administrador inicial e agendamento de coletas.

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

## Frontend acceptance criteria

- Administrator login uses the existing JWT endpoint; no token or password is saved in browser storage.
- Locale selection is available both before and after login, supports `pt-BR`, `en` and `es`, persists only that preference and updates `html.lang`.
- Every visible label, validation/error message, date and number follows the active locale. Catalog parity is tested.
- A user can paste a feed URL or select a real RSS/Atom example, trigger an authenticated run and see fetched/new/duplicate counts plus refreshed items.
- Source, title and collection-date filters, pagination, item details and safe HTTP(S) original links work through the existing API.
- Empty/loading/failure/expired-session states are implemented; duplicate-only collection is reported explicitly.
- Responsive mobile and desktop layouts support keyboard navigation and a native focus-trapping detail dialog.
- The frontend uses same-origin Vite/Nginx API proxies. Compose binds the UI to loopback by default.
- Unit, browser and production build checks pass; the complete flow is also exercised against the running Compose stack and real Atom input.

## 10. Perguntas em aberto

1. Cadastro de usuários pela interface ou só o administrador inicial por variável de ambiente? A spec assume só o administrador inicial.
2. Como será a remoção de um item sob pedido (seção 6.1)? Ainda não há endpoint para isso; a proposta é um `DELETE /v1/items/{id}` restrito a administrador, com registro do motivo.

## 11. Fases

- **Fase 1 (concluída no `develop`):** backend com API, JWT, API key, Swagger, migrações, coleta assíncrona com histórico; painel Vue com i18n; Docker Compose local.
- **Remoção sob pedido:** `DELETE /v1/items/{id}`, restrito ao admin (escopo `items:delete`), com motivo obrigatório e registro em `item_removal`. A coleta descarta itens removidos antes da gravação, então eles não voltam.
- **Validado com Compose:** backend, PostgreSQL e frontend sobem; coleta RSS real grava itens; remoção sob pedido impede a volta do item.
- **Fase 2:** deploy no Railway, depois de revisar a autenticação e as perguntas em aberto.
- **Fora de escopo:** agendamento de coletas e worker separado, que só entram se houver necessidade comprovada; a tabela `collection_run` já serve como fila.
