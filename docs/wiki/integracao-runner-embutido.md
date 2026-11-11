# Integração com o runner embutido

> O dashboard depende de três bibliotecas privadas irmãs, instaladas no Maven local por `scripts/build-local.sh` (ou copiadas em `libs/` para o build de imagem/Railway).

## Gotchas

- Ordem obrigatória: poc-collector → poc-rss-collector → poc-collector-runner → dashboard.
- A tabela `social_posts` do runner standalone é independente; nada é migrado automaticamente.
