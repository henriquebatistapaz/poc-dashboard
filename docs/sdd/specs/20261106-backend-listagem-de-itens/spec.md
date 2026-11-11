# SDD: backend-listagem-de-itens

**Complexity:** M

> SDD retroativa: documenta o que já foi entregue (commits bd1c59b e 71a1cb7). Escrita em 2026-11-06 a partir do código e do histórico do repo; status `Done`.

## 1. Context
A SPEC inicial definiu o dashboard como hub que reúne o que os projetos de coleta produzem.

## 2. Goal
Persistir itens normalizados no PostgreSQL e consultá-los com filtros e paginação.

## 3. Scope
### 3.1 In scope
- Tabela `collected_item` (V1) e entidade/repository.
- `GET /v1/items` (filtros source, q, from, to, page, size) e `GET /v1/items/{id}`.
- Proteção das consultas por API key.

### 3.2 Out of scope
- Coleta (fatia seguinte).
- Painel.

## 4. Requirements
### 4.1 Functional
- FR-01: Listagem ordenada por data de coleta e id desc.
- FR-02: `size` padrão 20, limitado a 1–100.
- FR-03: Intervalo de datas invertido → 400; id desconhecido → 404.

### 4.2 Non-functional
- —

## 5. Architecture and Design
Spring Data JPA + Flyway; records de resposta (`ItemResponse`, `ItemPageResponse`).

## 6. Implementation Plan
Já implementado. Principais pontos no código:
- `api/ItemController`
- `repository/CollectedItemRepository`
- `domain/CollectedItem`
- `db/migration/V1__create_collected_item.sql`

## 7. Acceptance Criteria
- AC-01: `ItemControllerTest` cobre filtros, paginação, 400 e 404.
- AC-02: Sem credencial válida a consulta é negada (`UnconfiguredApiKeyTest`).

## 8. Observability and Operations
- Sem requisitos específicos além dos logs padrão da aplicação.

## 9. Risks and Mitigations
- —

## 10. Rollback
Reverter os commits/PRs listados nas referências.

## 11. References
- commits bd1c59b e 71a1cb7
