# Pendências após a instalação do ai-init — 2026-11-06

Itens levantados na instalação/migração do ai-init para o `ai-init-internal`. Cada item vira SDD (`/new-spec`) ou tarefa quando for priorizado.

- [ ] Comandos de build/teste do `AGENTS.md` vieram do README, dos arquivos de build e dos scripts; ainda não foram executados para confirmar.
- [ ] Harness (fase 4 do ai-init) não configurado: CI com relatório de testes/cobertura, formatter, linter e piso de cobertura. Medir o estado atual antes de criar gates.
- [ ] Proteção da plataforma não verificada: branch protegida em `develop`/`main`, CI obrigatório, aprovação obrigatória, CODEOWNERS, secret scanning.
- [ ] Revisar as skills específicas do projeto em `.github/skills/` e a wiki gerada (`docs/wiki/`) contra o código.
- [ ] SDDs retroativas foram escritas a partir do código e do histórico; revisar se os critérios de aceite batem com o comportamento real.
- [x] O fallback de desenvolvimento `admin/admin` foi removido do código no PR #27; login agora depende de credenciais configuradas por ambiente e hash BCrypt.
- [ ] Conferir diretamente no Railway que `DASHBOARD_ADMIN_EMAIL` e `DASHBOARD_ADMIN_PASSWORD_HASH` estão configurados de forma própria em develop e production; o estado do ambiente production não foi verificado nesta atualização.
- [ ] Revisar o PR #29 (`develop` → `main`) antes de promover a fase 1.
