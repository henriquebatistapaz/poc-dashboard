# SDD: bloqueio-de-reingestao

**Complexity:** S

> SDD retroativa: documenta o que já foi entregue (PR #14 (commit 881b347)). Escrita em 2026-11-06 a partir do código e do histórico do repo; status `Done`.

## 1. Context
Depois da remoção, a próxima coleta trazia o item de volta.

## 2. Goal
Ignorar itens removidos ao ingerir.

## 3. Scope
### 3.1 In scope
- `RemovalAwareCollectionGateway` filtra itens com remoção registrada.

### 3.2 Out of scope
- —

## 4. Requirements
### 4.1 Functional
- FR-01: Item com registro em `item_removal` não é reinserido.

### 4.2 Non-functional
- —

## 5. Architecture and Design
Decorator sobre o gateway de coleta.

## 6. Implementation Plan
Já implementado. Principais pontos no código:
- `integration/RemovalAwareCollectionGateway`

## 7. Acceptance Criteria
- AC-01: Teste de API: remover e recoletar não reinsere o item.

## 8. Observability and Operations
- Sem requisitos específicos além dos logs padrão da aplicação.

## 9. Risks and Mitigations
- —

## 10. Rollback
Reverter os commits/PRs listados nas referências.

## 11. References
- PR #14 (commit 881b347)
