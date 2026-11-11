# SDD Index

The **Status column is the state machine of the cycle** — prompts read it to decide what runs, and `/implement-spec` refuses specs that aren't `Approved`. Keep it accurate.

| Status | Meaning | Who sets it |
|---|---|---|
| `Draft` | Spec written, not approved — may still change | `/new-spec` · `/spec-from-issue` |
| `Approved` | **Gate 1 passed — the human approval act IS changing this cell** (edit it yourself, or tell the agent "approved" and it updates + commits) | **Human only** |
| `In progress` | Being implemented | `/implement-spec` (on start) |
| `Shipped` | MR/PR open, waiting CI + human review (Gate 2) | `/open-mr` |
| `Done` | Merged | `/next` (on detecting the merge) or human |
| `Blocked` | Waiting on something external — say what in Notes | Anyone (state the reason) |

Rules: agents NEVER set `Approved`. A spec that needs rework after approval goes back to `Draft` (approval is re-required). Every spec directory must have a row here — an unlisted spec doesn't exist for the cycle.

| Spec | Issue | Status | Notes |
|------|-------|--------|-------|
| [`20261106-backend-listagem-de-itens/`](specs/20261106-backend-listagem-de-itens/spec.md) | — | Done | Fatia backend: persistência de itens e consulta protegida |
| [`20261106-coleta-autenticada-com-runner-embutido/`](specs/20261106-coleta-autenticada-com-runner-embutido/spec.md) | — | Done | Login admin (BCrypt+JWT), API key só leitura e coleta pelo runner embutido |
| [`20261106-coletas-assincronas-com-historico/`](specs/20261106-coletas-assincronas-com-historico/spec.md) | — | Done | Coletas em background com histórico persistido e recuperação |
| [`20261106-painel-vue-trilingue/`](specs/20261106-painel-vue-trilingue/spec.md) | — | Done | Painel Vue 3 pt-BR/en/es; histórico das dez execuções e retomada do polling após login |
| [`20261106-remocao-de-itens-com-auditoria/`](specs/20261106-remocao-de-itens-com-auditoria/spec.md) | — | Done | Remoção de itens só por admin, com motivo e auditoria |
| [`20261106-bloqueio-de-reingestao/`](specs/20261106-bloqueio-de-reingestao/spec.md) | — | Done | Item removido não volta em coletas futuras |
| [`20261106-deploy-railway/`](specs/20261106-deploy-railway/spec.md) | — | In progress | Develop no ar (API, painel, login e proxy verificados). Pendentes: ambiente production com credenciais próprias e verificação visual no navegador |
| [`20261106-filtro-por-fonte-e-logs-de-falha/`](specs/20261106-filtro-por-fonte-e-logs-de-falha/spec.md) | — | Done | Filtro aceita nomes de enum da fonte; log da classe da exceção em coletas que falham |
| [`20261106-gestao-persistente-de-usuarios/`](specs/20261106-gestao-persistente-de-usuarios/spec.md) | — | In progress | `ADMIN`/`OPERATOR`; admin inicial bootstrap por env; endpoints admin-only e hashes BCrypt |
| [`20261106-migracao-de-social-posts/`](specs/20261106-migracao-de-social-posts/spec.md) | — | In progress | Script PostgreSQL idempotente em `poc-dashboard/scripts/migrate-social-posts.sql`; requer tabelas no mesmo banco |
| [`20261106-revisao-de-deploy-publico/`](specs/20261106-revisao-de-deploy-publico/spec.md) | — | In progress | Hardening local do bootstrap admin e checklist feitos; inspeção das variáveis Railway production permanece pendente |
| [`20261106-agendamento-de-coletas/`](specs/20261106-agendamento-de-coletas/spec.md) | — | In progress | Implementado localmente com entrada cifrada, dispatcher periódico e recuperação após reinício; `mvn -B clean verify` passou com 48 testes no dashboard Java 27 em 2026-11-11 |
| [`20261106-ai-init-adocao-v2/`](specs/20261106-ai-init-adocao-v2/spec.md) | — | In progress | `fc05cc8` + `.ai-init`, workflows e contexto agora versionados na branch local; doc-check, guard `.env` e agent-setup verificados; GitHub CI aguarda publicação e revisão por IA depende de `ANTHROPIC_API_KEY` |
| [`20261110-atualizar-java-27/`](specs/20261110-atualizar-java-27/spec.md) | — | In progress | JDK 27.0.1.0.3, Boot 4.1.2; `mvn -B clean verify` passou com 48 testes em 2026-11-11; branch local, migração social e validação visual de produção seguem pendentes |
