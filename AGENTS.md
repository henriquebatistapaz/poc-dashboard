# AGENTS.md — Dashboard Mini

Guia operacional para agentes de IA (Copilot, Claude Code, Codex, Cursor etc.).
Contexto detalhado: @README.md, @SPEC.md, @DEPLOY.md, @frontend/README.md, @docs/ONBOARDING.md, @docs/sdd/constitution.md, @docs/wiki/index.md.

## O projeto em uma linha

Backend Java 27 / Spring Boot 4.1.2 (Maven, pacote raiz `com.henrique.dashboard`, PostgreSQL + Flyway) que embute o `poc-collector-runner` para coletar feeds RSS/Atom, fontes suportadas e fixtures sociais, persistir itens e consultá-los, com painel Vue 3 (pt-BR/en/es); código em inglês, SPEC em pt-BR.

## Comandos essenciais

```bash
# Bibliotecas privadas irmãs (poc-collector → poc-rss-collector → poc-collector-runner) + package do dashboard
./scripts/build-local.sh

# Infraestrutura local (PostgreSQL 16 + API + painel Vue/Nginx) — exige .env preenchido
docker compose up --build -d          # só o banco: docker compose up -d postgres

# Testes (sempre rodar antes de considerar uma tarefa concluída) — H2
mvn test # doc-check: run

# Integração real com PostgreSQL via Testcontainers (exige Docker)
mvn -Ppostgres-it verify

# Frontend (Node 22.12+)
cd frontend && npm ci && npm test && npm run build      # e2e: npm run test:e2e

# Rodar localmente (API em loopback; Swagger em /swagger-ui.html)
mvn spring-boot:run

# Hash BCrypt da senha do admin (sem eco)
./scripts/hash-admin-password.sh
```

Portas padrão do Compose: 15432 (banco), 8084 (API), 8085 (painel). Deploy no Railway: `DEPLOY.md` e `scripts/railway-deploy*.sh`.

## Convenções obrigatórias

- Pacotes por papel: `api` (controllers + records de request/response), `service`, `repository` (Spring Data), `domain` (entidades JPA com Lombok), `security`, `integration` (ponte com o runner embutido).
- Schema só via Flyway (`src/main/resources/db/migration/V<n>__*.sql`); o dashboard é dono de `collected_item`, `collection_run` e `item_removal`.
- Fontes são enums com nomes exatos (`RSS`, `GOOGLE_NEWS`, `GOOGLE_ALERTS`, `WORDPRESS`, `REDDIT`, `ALPHA`, `BETA`; `ISSUU` desligado).
- Deduplicação por `(source, external_id)`, preservando o primeiro item; IDs RSS levam hash da URL do feed. Item removido não volta a ser ingerido.
- Autenticação: usuários `ADMIN` e `OPERATOR` persistidos com BCrypt; JWT HS256 expira em 900 s; `X-API-Key` só leitura e nunca roda coleta. Admin inicial por configuração; cadastro público desativado.
- Coletas imediatas e agendadas rodam com histórico persistido (`CollectionRun`, `RunStatus`) e recuperação no boot (`CollectionRunRecovery`). Inputs agendados são cifrados com AES-GCM.
- Segredos: URLs do Google Alerts, chaves, JWT secret e credenciais Reddit nunca vão para log, resposta de erro ou git.
- Frontend: todo texto visível em `frontend/src/locales/{pt-BR,en,es}.json`; JWT só em memória; apenas preferências não sensíveis (`coleta.locale` e `poc-dashboard:theme`) vão para o storage.

## Project skills

Procedimentos recorrentes deste projeto em `.github/skills/`: `add-collection-source`, `add-flyway-migration`.

## Workflow (Spec-Driven Development)

Plataforma: GitHub (`gh` CLI). Branch de integração: `develop`; `main` recebe releases.

Toda spec nasce em `docs/sdd/specs/<YYYYMMDD>-<slug-kebab>/spec.md` (`<YYYYMMDD>` = data da criação, nunca renomeada; `<slug-kebab>` = nome curto) e é registrada no `docs/sdd/INDEX.md`. Specs antigas no formato `docs/sdd/<nome>/` ficaram como ponteiros de compatibilidade.

O trabalho entra por uma issue (`/spec-from-issue <url>`), um pedido direto, ou um achado de `/tech-debt`/`/retro`/`/metrics` (`/new-spec`) — bug trivial pula a spec (teste falhando → correção na raiz → validar).

A partir da spec, um funil só: revisão `@qa` + aprovação humana → `/test-plan` → `/implement-spec` → `/validate-implementation` (gate: ACs, build, `@reviewer`/`@qa`/`@architect`) → `/open-mr` (branch + PR) → CI → revisão humana → merge.

Veredito NOT READY (ou findings bloqueantes)? `/fix-findings` aplica — na raiz, mantendo a spec em sincronia — e o gate roda de novo. Agentes de revisão apontam; nunca editam.

Atalho: `/next` detecta o estado atual e executa o passo adequado, parando nos gates humanos.

Correções de bug: causa raiz, não sintoma — veja a skill `fix-root-cause` antes de escrever qualquer correção.

1. **Spec primeiro**: toda feature não trivial começa com uma SDD em `docs/sdd/<nome>/spec.md`, seguindo `docs/sdd/TEMPLATE.sdd.md`, registrada em `docs/sdd/INDEX.md`. O que já existia antes do ai-init está documentado em SDDs retroativas com status `Done`.
2. **Princípios**: respeite `docs/sdd/constitution.md` em qualquer plano ou implementação.
3. **Implementação**: siga o plano da SDD; valide contra os critérios de aceite antes de terminar (`/implement-spec`).
4. **Entrega**: `/validate-implementation` precisa retornar READY TO SHIP antes de `/open-mr`.

## Model routing (controle de custo)

Tiers disponíveis: use o seletor de modelos — rápido para edições rotineiras de SDD, padrão por padrão, raciocínio mais forte para design transversal ou sensível a segurança. Sem nomes fixos de modelo.

| Etapa | S | M | L |
|---|---|---|---|
| Escrita de spec / test plan | light | standard | standard |
| Implementação | standard | standard | **heavy** |
| Validação / reviews (@qa @reviewer @architect) | light | standard | **heavy** |
| Fix findings / causa raiz | standard | standard | **heavy** |
| Periódicos (/metrics /tech-debt /retro) | standard | standard | standard |

Escale por RACIOCÍNIO (design, causa raiz, contratos), não por volume; nunca revise abaixo do tier que implementou; na dúvida, um tier acima.

## Armadilhas (aprendidas do jeito difícil)

Problemas do projeto que já custaram uma correção. **Leia antes de planejar ou codar** — se a mudança tocar uma área listada aqui, siga a regra e diga isso no plano. Entradas são adicionadas pelo `/fix-findings` e pelo `/retro`.

<!-- Uma linha cada: - **<área>** — <o que quebra e por quê>. Regra: <o que fazer>. (fonte: <spec ou MR/PR>, <AAAA-MM-DD>) -->
_Nenhuma ainda._

## Não faça

- Não salve specs fora de `docs/sdd/` (a visão de produto em `SPEC.md`, se existir, continua na raiz).
- Não comite segredos ou credenciais (`.env` fica fora do git).
- Não introduza dependências sem justificar na SDD correspondente.
- Não considere uma tarefa concluída com testes falhando ou build quebrado.
- Não deixe `docs/wiki/`, `README.md` ou este arquivo desatualizados quando uma mudança alterar o que descrevem.
- Os arquivos do ai-init (`.ai-init.json`, `AGENTS.md`, `CLAUDE.md`, `.ai-init`, `.github/copilot-instructions.md`, `.github/prompts|agents|instructions|skills`) e os docs do ciclo (`docs/sdd`, `docs/wiki`, `docs/ONBOARDING.md`, `docs/tech-debt`) são links para `../ai-init-internal/<projeto>/` e NÃO são versionados neste repo. Edite normalmente; o commit vai no repo `ai-init-internal`.
- Não exponha a API ou o banco fora de loopback no Compose local.
- Não use as credenciais de desenvolvimento (admin/admin do develop) em produção.
