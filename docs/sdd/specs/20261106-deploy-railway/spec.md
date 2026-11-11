# SDD: deploy-railway

**Complexity:** M

> Atualizada em 2026-11-06 com o estado real após o deploy de develop e com o Dockerfile atual do `develop` (libs vendorizadas em `libs/`). Referências: PR #15, #18 e #19 do repo `poc-dashboard`.

## 1. Context
O dashboard precisa rodar fora da máquina local, com API e painel acessíveis por URL pública, sem depender do Compose.

## 2. Goal
Publicar API e painel no Railway, com um ambiente `develop` funcional e um ambiente `production` pronto para deploy por script.

## 3. Scope
### 3.1 In scope
- `Dockerfile` autocontido: compila as bibliotecas coletoras copiadas de `libs/` (versão vendorizada no próprio repo, atualizada por `scripts/sync-libs.sh`). O build não precisa de token.
- `railway.toml` da API (health check em `/v3/api-docs`) e `frontend/railway.toml` do painel.
- Painel (nginx) com o upstream da API definido por `API_UPSTREAM`, via template oficial da imagem nginx.
- Porta da aplicação lida de `PORT`; bind em `SERVER_ADDRESS`.
- Scripts `scripts/railway-deploy.sh`, `railway-deploy-develop.sh` e `railway-deploy-production.sh`.
- Guia `DEPLOY.md`.
- Ambiente `develop` no projeto Railway `poc-dashboard`, com Postgres, API e web.

### 3.2 Out of scope
- Ambiente `production` com credenciais reais: fica para depois da revisão de autenticação e das perguntas em aberto da spec principal.
- Publicação das bibliotecas no GitHub Packages (alternativa ao clone durante o build).

## 4. Requirements
### 4.1 Functional
- FR-01: `scripts/railway-deploy-develop.sh` roda testes, exige branch `develop` e árvore limpa, e envia API e web.
- FR-02: `scripts/railway-deploy-production.sh` exige branch `main`, árvore limpa e confirmação digitada.
- FR-03: O painel encaminha `/v1/*`, `/swagger-ui` e `/v3/api-docs` para a API.

### 4.2 Non-functional
- NFR-01: O build não usa token. As bibliotecas vêm de `libs/` versionado no repo; a atualização é feita por `scripts/sync-libs.sh [branch]`, que exige `gh` autenticado apenas para clonar, fora do build.
- NFR-03: A imagem define `SERVER_ADDRESS=::` (todas as interfaces, IPv4 e IPv6, necessário na rede privada do Railway). `PORT` continua sendo lido pela aplicação.
- NFR-02: A API escuta em `0.0.0.0` e na porta `8080`. O web escuta na porta `80`.

## 5. Architecture and Design
- Serviços no projeto `poc-dashboard`: `poc-dashboard-api` (raiz do repo), `poc-dashboard-web` (diretório `frontend`), plugin Postgres.
- Variáveis da API: `DB_URL`, `DB_USER`, `DB_PASSWORD` (referências ao Postgres), `DASHBOARD_API_KEY`, `DASHBOARD_JWT_SECRET`, `DASHBOARD_ADMIN_EMAIL`, `DASHBOARD_ADMIN_PASSWORD_HASH`, `REDDIT_API_ACCESS_APPROVED=false`, `PORT=8080`. `GITHUB_TOKEN` não é mais necessário no build.
- Variáveis do web: `API_UPSTREAM=http://poc-dashboard-api.railway.internal:8080`, `PORT=80`.
- Ordem de deploy: após qualquer redeploy da API, redeployar o web. O nginx resolve o endereço da API no startup; sem isso, o web pode responder 502.

## 6. Implementation Plan
Implementado no `develop` (PR #15, #18, #19):
- `Dockerfile`, `.dockerignore`, `railway.toml`, `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/railway.toml`.
- `scripts/railway-deploy*.sh` e `DEPLOY.md`.
- `src/main/resources/application.yml` com `server.port` lido de `PORT`.

Feito no Railway, em 2026-11-06: projeto `poc-dashboard`, ambiente `develop`, serviços e variáveis; deploy de develop com API e web em SUCCESS.

## 7. Acceptance Criteria
- AC-01 (atendido): `GET /v3/api-docs` da API retorna 200 no domínio público do develop.
- AC-02 (atendido): login via API e via painel retorna 200 com o administrador de validação.
- AC-03 (atendido): `GET /v1/items` sem token pelo painel retorna 401, confirmando a proxy.
- AC-04 (pendente): ambiente `production` criado com administrador e segredos próprios, sem reutilizar credenciais de teste.
- AC-05 (pendente): o painel é verificado num navegador, não só por HTTP.

## 8. Observability and Operations
- Logs padrão da aplicação via `railway logs`.
- Health check da API em `/v3/api-docs`.

## 9. Risks and Mitigations
- **Drift das bibliotecas vendorizadas**: `libs/` pode ficar desatualizado em relação aos repos de origem. Mitigação: atualizar com `scripts/sync-libs.sh` e registrar o commit de origem; ver achado 1 da dívida técnica.
- **Token de GitHub de escopo amplo** (usado no `gh` local para `sync-libs.sh`): usar token fine-grained só de leitura nos três repos.
- **Administrador de validação em develop**: restrito ao ambiente develop; produção usa outro administrador.
- **Nginx com endereço da API fixado no startup**: mitigado pela ordem de deploy documentada; solução definitiva é resolver o nome sob demanda.

## 10. Rollback
Reverter os PRs #15, #18 e #19 no `poc-dashboard`, ou redeployar o commit anterior pela CLI do Railway.

## 11. References
- PR #15 (deploy autocontido), PR #18 (token como argumento de build e web a partir de `frontend`, substituído pelo `libs/` vendorizado), PR #19 (notas do deploy).
