# Deploy no Railway

> API e painel são serviços separados; detalhes e ordem de deploy em `DEPLOY.md`.

## Gotchas

- O build da imagem não usa cache mount (não suportado no Railway).
- O Nginx do painel resolve o endereço da API dinamicamente.
- As bibliotecas privadas entram via `libs/`, sem token no build.
