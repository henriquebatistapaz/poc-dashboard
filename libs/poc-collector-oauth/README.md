# poc-collector-oauth

Coletor Java 27 (Maven, Jackson, JUnit) focado no Mastodon. Usa a API oficial do Mastodon: busca de posts públicos por termo (`GET /api/v2/search?type=statuses`) com token de acesso `Bearer`.

## Requisitos

- JDK 27 e Maven 3.9+ (ou `./mvnw`).
- Token de acesso da instância com escopo `read:search`.

## Build e testes

```bash
./mvnw -B verify
```

## Uso

```bash
MASTODON_ACCESS_TOKEN=... ./mvnw -q -DskipTests package
MASTODON_ACCESS_TOKEN=... java -jar target/poc-collector-oauth-0.1.0-SNAPSHOT.jar "open data" 20
```

`MASTODON_INSTANCE` é opcional (padrão `https://mastodon.social`) e precisa usar HTTPS.

## Regras de segurança

- O token vem só do ambiente. Nunca é impresso nem aparece em mensagens de erro.
- Redirects não são seguidos, para o token não sair da instância informada.
- Só posts com visibilidade `public` são retornados.
- Resposta limitada a 2 MB; termo de busca limitado a 500 caracteres; limite de resultados de 1 a 40.

## Login OAuth

`MastodonOAuthClient` implementa o fluxo authorization_code: registra o app (`POST /api/v1/apps`), monta a URL de autorização com `state` aleatório e troca o código por token (`POST /oauth/token`). Escopo: `read:search`. `redirect_uri` precisa ser HTTPS ou loopback. O `client_secret` nunca é impresso.

## Operações de escrita

`MastodonWriteService` publica, curte e apaga status na instância autorizada:

- `publish(texto, visibilidade)`: `POST /api/v1/statuses`. Visibilidade: `public` (padrão), `unlisted`, `private` ou `direct`. Limite de 500 caracteres.
- `favourite(id)`: `POST /api/v1/statuses/{id}/favourite`.
- `delete(id)`: `DELETE /api/v1/statuses/{id}`. Só apaga status do próprio token.

Exige escopos `write:statuses` e `write:favourites`, já incluídos no fluxo OAuth. Ids são validados antes de qualquer chamada de rede.

## API HTTP para frontend

Backend em JSON, sem framework (JDK HttpServer), escutando só em `127.0.0.1`.

```bash
PORT=8080 FRONTEND_URL=http://localhost:5173/ ./mvnw -q -DskipTests package
java -cp target/classes dev.henrique.mastodon.api.ApiMain
```

Variáveis: `PORT` (8080), `FRONTEND_URL` (`http://localhost:5173/`), `OAUTH_REDIRECT_URI` (padrão `http://127.0.0.1:8080/api/oauth/callback`), `COOKIE_SECURE` (`false`; use `true` com HTTPS).

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/health` | Status do serviço |
| GET | `/api/oauth/login?instance=https://mastodon.social` | Devolve `authorizationUrl` para o usuário abrir |
| GET | `/api/oauth/callback?code=&state=` | Troca o código, cria sessão (cookie `poc_sid`) e redireciona ao frontend |
| GET | `/api/session` | `{authenticated, instance}` |
| POST | `/api/session/logout` | Encerra a sessão |
| GET | `/api/search?q=termo&limit=20` | Posts públicos (1 a 40 resultados) |
| POST | `/api/statuses` | Corpo `{"text": "...", "visibility": "public"}` → 201 com o post |
| POST | `/api/statuses/{id}/favourite` | `{"favourited": true}` |
| DELETE | `/api/statuses/{id}` | 204 |

Regras para o frontend:
- Toda chamada usa `credentials: "include"`. O CORS aceita só a origem de `FRONTEND_URL`.
- Escritas (POST e DELETE) exigem o cabeçalho `X-Requested-With` (qualquer valor não vazio).
- Corpo de publicação em `application/json`.
- Tokens ficam só no servidor, em memória. Reiniciar o servidor encerra as sessões.
- Frontend e API precisam estar na mesma origem em desenvolvimento (proxy do Vite, por exemplo), porque o cookie tem `SameSite=Lax`. Para origens diferentes, use HTTPS com `COOKIE_SECURE=true` e `SameSite=None`.
