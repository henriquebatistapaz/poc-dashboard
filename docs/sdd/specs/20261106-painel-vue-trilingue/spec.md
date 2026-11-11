# SDD: painel-vue-trilingue

**Complexity:** M

> SDD retroativa do painel multilíngue e do acompanhamento de coletas. O histórico retomável foi atualizado no PR #27, integrado em `develop`. Verificação atual registrada na SPEC do projeto: 15 testes Vitest e 6 testes determinísticos Playwright aprovados; status `Done`.

## 1. Context
A API precisava de uma interface para o admin.

## 2. Goal
Painel local para login, teste de feed, coleta e consulta de itens nos três idiomas.

## 3. Scope
### 3.1 In scope
- Vue 3 + vue-i18n + Vite, servido por Nginx no Compose.
- Login, formulário de coleta, contagens novos/duplicados, lista filtrada/paginada, detalhe.
- Exibição das dez execuções recentes.
- Polling da execução assíncrona e retomada do acompanhamento de execuções pendentes após novo login.
- Cancelamento do polling no logout e intervalo com backoff limitado.

### 3.2 Out of scope
- Persistência do JWT no navegador.

## 4. Requirements
### 4.1 Functional
- FR-01: Troca de idioma sem recarregar.
- FR-02: JWT só em memória; só o idioma vai para o storage.
- FR-03: URL do Google Alerts mascarada e limpa após sucesso.

### 4.2 Non-functional
- —

## 5. Architecture and Design
SPA na mesma origem da API (proxy Nginx), sem CORS permissivo.

## 6. Implementation Plan
Já implementado. Principais pontos no código:
- `frontend/src/**`
- `frontend/nginx.conf`
- `frontend/e2e/**`

## 7. Acceptance Criteria
- AC-01: `npm test` (Vitest) e `npm run test:e2e` (Playwright) verdes.
- AC-02: Todos os textos existem nos três locales.
- AC-03: O painel exibe até dez execuções recentes e retoma o acompanhamento de uma execução pendente após novo login.
- AC-04: O polling é encerrado no logout e seu intervalo aumenta com backoff limitado enquanto a execução não termina.

## 8. Observability and Operations
- Sem requisitos específicos além dos logs padrão da aplicação.

## 9. Risks and Mitigations
- —

## 10. Rollback
Reverter os commits/PRs listados nas referências.

## 11. References
- commit 093d87f
