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
| `REDDIT_API_ACCESS_APPROVED` | `false` até a aprovação do Reddit |
| `GITHUB_TOKEN` | token com leitura nos repos `collector-mini`, `rss-collector-mini`, `collector-runner-mini` (usado só no build) |

Para `production`, use valores diferentes dos de `develop`, principalmente `DASHBOARD_JWT_SECRET` e `DASHBOARD_API_KEY`.

## 3. Build dos coletores

O Dockerfile clona e instala as três bibliotecas durante o build, com `GITHUB_TOKEN`. O token é passado como **BuildKit secret**, então não fica gravado nas camadas da imagem. Teste local:

```bash
GITHUB_TOKEN=$(gh auth token) docker build --secret id=github_token,env=GITHUB_TOKEN -t dashboard-mini:local .
```

> Ponto a confirmar no Railway: se o builder do Railway não expuser o segredo como BuildKit secret, o build falha com `cannot read /run/secrets/github_token`. Nesse caso, a alternativa é publicar as bibliotecas no GitHub Packages.

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
