# SDD: coletas-assincronas-com-historico

**Complexity:** M

> SDD retroativa: documenta o que já foi entregue (commits e6e8257, fad0e42, acee9da). Escrita em 2026-11-06 a partir do código e do histórico do repo; status `Done`.

## 1. Context
Coletas síncronas travavam a requisição e não deixavam histórico.

## 2. Goal
Rodar coletas em background e guardar cada execução.

## 3. Scope
### 3.1 In scope
- Tabela `collection_run` (V2), `CollectionRun`, `RunStatus`.
- Serviço assíncrono e consulta do status da execução.
- Recuperação de execuções interrompidas no boot.

### 3.2 Out of scope
- Agendamento periódico.

## 4. Requirements
### 4.1 Functional
- FR-01: Disparar coleta retorna a execução, que pode ser consultada até terminar.
- FR-02: Execuções presas são marcadas no boot.

### 4.2 Non-functional
- —

## 5. Architecture and Design
`CollectionRunService` + executor configurado em `CollectionRunConfiguration`; `CollectionRunRecovery` no startup.

## 6. Implementation Plan
Já implementado. Principais pontos no código:
- `service/CollectionRunService`
- `service/CollectionRunRecovery`
- `db/migration/V2__create_collection_run.sql`

## 7. Acceptance Criteria
- AC-01: `CollectionRunApiTest` acompanha a execução até o fim.
- AC-02: Testes aguardam a execução em background sem vazar entre casos.

## 8. Observability and Operations
- Sem requisitos específicos além dos logs padrão da aplicação.

## 9. Risks and Mitigations
- —

## 10. Rollback
Reverter os commits/PRs listados nas referências.

## 11. References
- commits e6e8257, fad0e42, acee9da
