# SDD: coleta-autenticada-com-runner-embutido

**Complexity:** L

> SDD retroativa: documenta o que já foi entregue (commits eccd537 e a8be8f3). Escrita em 2026-11-06 a partir do código e do histórico do repo; status `Done`.

## 1. Context
O dashboard precisava disparar coletas sem um servidor separado do runner e com autenticação.

## 2. Goal
Admin autenticado roda coletas pelas bibliotecas embutidas e grava itens novos.

## 3. Scope
### 3.1 In scope
- `POST /v1/auth/login` com admin de configuração, BCrypt e JWT HS256.
- `POST /v1/collections` (source + input) chamando o runner embutido.
- Deduplicação `(source, external_id)` e rollback do lote em falha.
- Script `hash-admin-password.sh`.

### 3.2 Out of scope
- Cadastro de usuários.
- Reddit sem aprovação oficial.

## 4. Requirements
### 4.1 Functional
- FR-01: Login devolve `accessToken`, `tokenType=Bearer`, `expiresIn=900`.
- FR-02: API key não roda coleta (403).
- FR-03: Resposta da coleta: `source`, `fetched`, `inserted`, `duplicates`.
- FR-04: Erros 400/401/403/422/502/503 conforme README, sem refletir entrada secreta.

### 4.2 Non-functional
- JWT secret ≥ 32 bytes; senha 1–72 bytes.
- Entrada ≤ 4.096 caracteres; ≤ 1.000 itens por execução.

## 5. Architecture and Design
`RunnerConfiguration` monta o runner como bean; `SecurityConfiguration` separa JWT (admin) de API key (leitura).

## 6. Implementation Plan
Já implementado. Principais pontos no código:
- `api/AuthController`
- `api/CollectionController`
- `security/*`
- `integration/RunnerConfiguration`

## 7. Acceptance Criteria
- AC-01: `CollectionApiTest` cobre coleta autenticada, duplicatas e erros.
- AC-02: `DashboardRunnerPostgresIT` coleta RSS/Atom, Google, WordPress e Alpha/Beta com fixtures.

## 8. Observability and Operations
- Sem requisitos específicos além dos logs padrão da aplicação.

## 9. Risks and Mitigations
- Vazamento de URLs secretas → nunca logadas nem ecoadas.

## 10. Rollback
Reverter os commits/PRs listados nas referências.

## 11. References
- commits eccd537 e a8be8f3
