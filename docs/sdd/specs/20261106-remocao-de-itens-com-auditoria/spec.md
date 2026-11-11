# SDD: remocao-de-itens-com-auditoria

**Complexity:** M

> SDD retroativa: documenta o que já foi entregue (PR #13 (commit 80f1a27)). Escrita em 2026-11-06 a partir do código e do histórico do repo; status `Done`.

## 1. Context
Itens indevidos precisavam sair da listagem com rastreabilidade.

## 2. Goal
Admin remove item informando motivo; fica registro de auditoria.

## 3. Scope
### 3.1 In scope
- Tabela `item_removal` (V3), `ItemRemoval`, `ItemRemovalService`, `ItemRemovalRequest`.

### 3.2 Out of scope
- Restauração de item removido.

## 4. Requirements
### 4.1 Functional
- FR-01: Só admin (JWT) remove; API key não.
- FR-02: Motivo obrigatório.
- FR-03: Registro de auditoria com quem/quando/motivo.

### 4.2 Non-functional
- —

## 5. Architecture and Design
Endpoint no `ItemController` delegando para `ItemRemovalService`.

## 6. Implementation Plan
Já implementado. Principais pontos no código:
- `service/ItemRemovalService`
- `repository/ItemRemovalRepository`
- `db/migration/V3__create_item_removal.sql`

## 7. Acceptance Criteria
- AC-01: `ItemRemovalApiTest` cobre remoção, permissão e motivo obrigatório.

## 8. Observability and Operations
- Sem requisitos específicos além dos logs padrão da aplicação.

## 9. Risks and Mitigations
- —

## 10. Rollback
Reverter os commits/PRs listados nas referências.

## 11. References
- PR #13 (commit 80f1a27)
