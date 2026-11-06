# Deploy no Railway — dashboard-mini

Dois ambientes: **develop** (branch `develop`) e **production** (branch `main`). Em cada um há dois serviços e um Postgres.

## Serviços

| Serviço | Pasta (Root Directory) | Config | Porta |
|---|---|---|---|
| `dashboard-mini-api` | raiz (`/`) | `railway.toml` + `Dockerfile` | `PORT` (Railway injeta) |
| `dashboard-mini-web` | `frontend` | `frontend/railway.toml` + `frontend/Dockerfile` | 80 |
| `postgres` | plugin Railway | — | 5432 |

O serviço web encaminha `/v1/*`, `/swagger-ui` e `/v3/api-docs` para a API. No Railway, a API precisa ser alcançável pelo nome interno: defina em `dashboard-mini-web` a variável `API_UPSTREAM=http://dashboard-mini-api.railway.internal:8080`.

## 1. Criar o projeto

1. Railway → **New Project** → **Empty project**, nome `dashboard-mini`.
2. Crie os ambientes **develop** e **production**.
3. Em cada ambiente, adicione o plugin **PostgreSQL**.
4. Em cada ambiente, adicione o serviço **dashboard-mini-api** (GitHub repo `henriquebatistapaz/dashboard-mini`, branch `develop` ou `main`, Root Directory `/`).
5. Adicione o serviço **dashboard-mini-web** (mesmo repo, Root Directory `frontend`).

## 2. Variáveis da API (`dashboard-mini-api`)

| Variável | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USER` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `DASHBOARD_API_KEY` | chave longa e aleatória (`openssl rand -hex 24`) |
| `DASHBOARD_ADMIN_EMAIL` | e-mail do administrador |
| `DASHBOARD_ADMIN_PASSWORD_HASH` | hash BCrypt (`scripts/hash-admin-password.sh`) |
| `DASHBOARD_JWT_SECRET` | segredo de 32+ bytes (`openssl rand -hex 32`), diferente em cada ambiente |
| `SERVER_ADDRESS` | `0.0.0.0` (sem isso a aplicação só escuta em 127.0.0.1 e o Railway retorna 502) |
| `PORT` | `8080` (alinhado ao `API_UPSTREAM` do web) |
| `REDDIT_API_ACCESS_APPROVED` | `false` até a aprovação do Reddit |
| `GITHUB_TOKEN` | token com leitura nos repos `collector-mini`, `rss-collector-mini`, `collector-runner-mini` (usado só no build) |

## 2b. Variáveis do web (`dashboard-mini-web`)

| Variável | Valor |
|---|---|
| `API_UPSTREAM` | `http://dashboard-mini-api.railway.internal:8080` |
| `PORT` | `80` (o nginx escuta na 80; sem isso o domínio retorna 502) |

> Ordem de deploy: o nginx resolve o endereço da API quando sobe. Depois de redeploy da **API**, faça também redeploy do **web** (`railway redeploy --service dashboard-mini-web -y`), senão o web pode continuar apontando para o endereço antigo e responder 502.

Para `production`, use valores diferentes dos de `develop`, principalmente `DASHBOARD_JWT_SECRET` e `DASHBOARD_API_KEY`.

## 3. Build dos coletores

O Dockerfile clona e instala as três bibliotecas durante o build, com `GITHUB_TOKEN` como argumento de build. O token fica só na etapa intermediária de build; a imagem final não o contém. Teste local:

```bash
docker build --build-arg GITHUB_TOKEN=$(gh auth token) -t dashboard-mini:local .
```

> Recomendação: use um token fine-grained somente leitura, restrito aos três repos de bibliotecas, em vez do token do `gh` (que tem escopo amplo). O token fica em cache da etapa intermediária do builder do Railway; a alternativa definitiva é publicar as bibliotecas no GitHub Packages.

## 4. Deploy

Pré-requisitos: Railway CLI (`brew install railway`), `railway login` e `railway link` apontando para o projeto.

```bash
scripts/railway-deploy-develop.sh        # branch develop, sem confirmação
scripts/railway-deploy-production.sh     # branch main, pede para digitar "production"
```

Os scripts recusam deploy com árvore suja ou na branch errada, rodam `mvn test`, `npm test` e `npm run build`, e então enviam API e web com `railway up`.

## 5. Verificação

- API: `GET https://<api>/v3/api-docs` responde 200.
- Login: `POST https://<web>/v1/auth/login` com as credenciais do administrador.
- Painel: `https://<web>/` carrega a tela de login.
