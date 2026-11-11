# Architecture overview

> Dashboard que coleta itens de várias fontes por meio do runner embutido, guarda no PostgreSQL e expõe consulta por API e painel Vue.

## Flow

```mermaid
flowchart LR
    U[Admin no painel Vue] -->|POST /v1/auth/login| AU(AuthController)
    U -->|POST /v1/collections + JWT| CC(CollectionController)
    CC --> RS(CollectionRunService)
    RS -->|async| GW(RemovalAwareCollectionGateway)
    GW --> R[poc-collector-runner embutido]
    R --> L[poc-rss-collector / poc-collector]
    GW --> DB[(PostgreSQL: collected_item, collection_run, item_removal)]
    C[Cliente leitura + X-API-Key] -->|GET /v1/items| IC(ItemController) --> DB
```

## Key classes / entry points

| Class | Role |
|-------|------|
| `CollectionController` / `CollectionRunService` | Dispara e acompanha coletas assíncronas |
| `RemovalAwareCollectionGateway` | Chama o runner e ignora itens removidos |
| `ItemController` | Listagem filtrada/paginada e detalhe |
| `ItemRemovalService` | Remoção com motivo e auditoria |
| `SecurityConfiguration`, `AdminLoginService`, `JwtConfiguration` | Login admin, JWT e API key |
| `CollectionRunRecovery` | Marca execuções interrompidas no boot |

## Related

- [[integracao-runner-embutido]], [[dominio-coleta-e-itens]], [[dominio-autenticacao]], [[ops-deploy-railway]], `SPEC.md`
