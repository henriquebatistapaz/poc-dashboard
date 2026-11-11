# Autenticação

## Business rules

- Admin vem de configuração (`DASHBOARD_ADMIN_EMAIL` + hash BCrypt); sem cadastro.
- JWT HS256, 900 s, secret com pelo menos 32 bytes; sem admin configurado o login responde 503.
- `X-API-Key` só lê; nunca roda coleta (403).

## Gotchas

- O develop no Railway usa admin/admin para facilitar testes — nunca replicar em produção.
