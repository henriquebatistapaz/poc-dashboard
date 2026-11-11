---
name: add-collection-source
description: Habilitar ou ajustar uma fonte de coleta (enum de source) no poc-dashboard usando o runner embutido.
---

# Adicionar/ajustar uma fonte de coleta

1. A fonte precisa existir antes nas bibliotecas irmãs (`poc-collector`/`poc-collector-runner`); rode `./scripts/build-local.sh` para instalar a versão local.
2. Exponha o nome exato do enum na validação de `POST /v1/collections` e no filtro `source` de `GET /v1/items` (veja o ajuste do PR #17).
3. Trate segredos de entrada (como URLs do Google Alerts) como sensíveis: nada de log ou eco em erro.
4. Testes: caso feliz e negativo em `CollectionApiTest`; fluxo real com fixture determinística em `DashboardRunnerPostgresIT` (`mvn -Ppostgres-it verify`).
5. Painel: opção nova e textos nos três arquivos de `frontend/src/locales/`.
6. Atualize README, SPEC.md e `docs/wiki/dominio-coleta-e-itens.md`.
