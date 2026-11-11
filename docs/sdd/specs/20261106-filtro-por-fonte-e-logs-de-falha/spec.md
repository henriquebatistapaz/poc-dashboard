# SDD: filtro-por-fonte-e-logs-de-falha

**Complexity:** S

> SDD retroativa: documenta o que já foi entregue (PRs #16 e #17). Escrita em 2026-11-06 a partir do código e do histórico do repo; status `Done`.

## 1. Context
O filtro `source` recusava nomes de enum e falhas de coleta eram pouco diagnosticáveis.

## 2. Goal
Aceitar os nomes exatos do enum e registrar a classe da exceção.

## 3. Scope
### 3.1 In scope
- Filtro `source` com nomes do enum.
- Log da classe da exceção na execução que falha.
- SPEC atualizada após validação no Compose.

### 3.2 Out of scope
- —

## 4. Requirements
### 4.1 Functional
- FR-01: `GET /v1/items?source=RSS` funciona.
- FR-02: Execução com falha registra a classe da exceção.

### 4.2 Non-functional
- —

## 5. Architecture and Design
Ajustes pontuais em controller/serviço.

## 6. Implementation Plan
Já implementado. Principais pontos no código:
- `api/ItemController`
- `service/CollectionRunService`

## 7. Acceptance Criteria
- AC-01: Teste do filtro por enum verde.

## 8. Observability and Operations
- Sem requisitos específicos além dos logs padrão da aplicação.

## 9. Risks and Mitigations
- —

## 10. Rollback
Reverter os commits/PRs listados nas referências.

## 11. References
- PRs #16 e #17
