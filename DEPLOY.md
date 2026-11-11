# Deploy no Railway — poc-dashboard

Build Java: OpenJDK 27.0.1.0.3, Spring Boot 4.1.2 e Maven 3.9 ou superior.

Dois ambientes: **develop** (branch `develop`) e **production** (branch `main`). Em cada um há dois serviços e um Postgres.

## Serviços

| Serviço | Pasta (Root Directory) | Config | Porta |
|---|---|---|---|
| `poc-dashboard-api` | raiz (`/`) | `railway.toml` + `Dockerfile` | `PORT` (Railway injeta) |
| `poc-dashboard-web` | `frontend` | `frontend/railway.toml` + `frontend/Dockerfile` | 80 |
| `postgres` | plugin Railway | — | 5432 |

O serviço web encaminha `/v1/*`, `/swagger-ui` e `/v3/api-docs` para a API. No Railway, a API precisa ser alcançável pelo nome interno: defina em `poc-dashboard-web` a variável `API_UPSTREAM=http://poc-dashboard-api.railway.internal:8080`.

## 1. Criar o projeto

1. Railway → **New Project** → **Empty project**, nome `poc-dashboard`.
2. Crie os ambientes **develop** e **production**.
3. Em cada ambiente, adicione o plugin **PostgreSQL**.
4. Em cada ambiente, adicione o serviço **poc-dashboard-api** (GitHub repo `henriquebatistapaz/poc-dashboard`, branch `develop` ou `main`, Root Directory `/`).
5. Adicione o serviço **poc-dashboard-web** (mesmo repo, Root Directory `frontend`).

## 2. Variáveis da API (`poc-dashboard-api`)

| Variável | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USER` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `DASHBOARD_API_KEY` | chave longa e aleatória (`openssl rand -hex 24`) |
| `DASHBOARD_ADMIN_EMAIL` | e-mail do administrador |
| `DASHBOARD_ADMIN_PASSWORD` | senha do admin (no Railway: `${{secret(24)}}`); ou `DASHBOARD_ADMIN_PASSWORD_HASH` com hash BCrypt |
| `DASHBOARD_JWT_SECRET` | segredo de 32+ bytes (`openssl rand -hex 32`), diferente em cada ambiente |
| `SERVER_ADDRESS` | `0.0.0.0` (sem isso a aplicação só escuta em 127.0.0.1 e o Railway retorna 502) |
| `PORT` | `8080` (alinhado ao `API_UPSTREAM` do web) |
| `REDDIT_API_ACCESS_APPROVED` | `false` até a aprovação do Reddit |

## 2b. Variáveis do web (`poc-dashboard-web`)

| Variável | Valor |
|---|---|
| `API_UPSTREAM` | `http://poc-dashboard-api.railway.internal:8080` |

O nome interno continua `poc-dashboard-api`: o Railway só mudou o nome do serviço, não o hostname privado. Não troque esse valor sem antes confirmar o hostname em `RAILWAY_PRIVATE_DOMAIN` do serviço da API.
| `PORT` | `80` (o nginx escuta na 80; sem isso o domínio retorna 502) |

> Ordem de deploy: o nginx resolve o endereço da API quando sobe. Depois de redeploy da **API**, faça também redeploy do **web** (`railway redeploy --service poc-dashboard-web -y`), senão o web pode continuar apontando para o endereço antigo e responder 502.

Para `production`, use valores diferentes dos de `develop`, principalmente `DASHBOARD_JWT_SECRET` e `DASHBOARD_API_KEY`.

## 3. Build dos coletores

O código das bibliotecas privadas (`poc-collector`, `poc-rss-collector`, `poc-collector-runner`) fica em `libs/`. O Dockerfile instala as três a partir dali, então o build não precisa de token. Para atualizar `libs/` com o `main` das bibliotecas:

```bash
scripts/sync-libs.sh
```

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
