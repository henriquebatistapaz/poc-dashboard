# Dashboard Mini — Especificação inicial

**Status:** Rascunho para revisão. Nenhum código implementado.
**Repositório:** Privado
**Fase desta spec:** proposta para discussão — backend (Spring Boot, PostgreSQL, Swagger, autenticação) e painel Vue separado, com coleta agendada e sob demanda. Nenhuma decisão arquitetural foi aprovada.

## 1. Objetivo

Um sistema com frontend e backend que lê os itens coletados e guardados no banco pelo `collector-runner-mini` e permite:

- consultar os itens coletados (filtros por fonte, palavra-chave e período);
- disparar uma coleta sob demanda pelo `collector-runner-mini`;
- deixar coletas agendadas por palavra-chave e feed.

O resultado é para leitura pessoal e para consumo por outro sistema via API. Alertas de novidade ficam fora desta fase.

## 2. Relação com os outros projetos

| Projeto | Papel |
|---|---|
| `collector-mini` | Coleta Google News, Google Alerts, WordPress e Reddit. Biblioteca Maven. |
| `rss-collector-mini` | Valida e lê feeds RSS/Atom. Biblioteca Maven. |
| `collector-runner-mini` | Chama os coletores, normaliza, deduplica e grava no PostgreSQL. Biblioteca Maven e serviço. |
| `dashboard-mini` (este) | Lê o banco, expõe a API, dispara coletas pelo `collector-runner-mini` e serve o painel. |

**Proposta para discussão, não decisão aprovada:** integração por biblioteca. O hub declararia o `collector-runner-mini` como dependência Maven e chamaria os serviços dele dentro do mesmo processo. A alternativa de integração via HTTP ainda precisa ser avaliada.

**Proposta para discussão, não decisão aprovada:** o `collector-runner-mini` definiria as tabelas e migrações Flyway dos itens, e o hub as leria. A propriedade dos dados e do esquema precisa ser confirmada antes da implementação.

## 3. Requisitos funcionais

- **RF1 — Listar itens:** `GET /v1/items` com paginação e filtros por fonte, palavra-chave (texto do título) e intervalo de datas.
- **RF2 — Detalhar item:** `GET /v1/items/{id}` com fonte, id externo, título, link original e datas.
- **RF3 — Disparar coleta:** `POST /v1/collections` recebe fonte e entrada (palavra-chave, URL ou subreddit) e chama o `collector-runner-mini`. Devolve quantos itens foram gravados e quantos eram novos.
- **RF4 — Agendar coleta:** cadastro de coletas recorrentes (fonte, entrada, intervalo em minutos), executadas por um agendador do próprio hub.
- **RF5 — Login no painel:** usuário e senha, com emissão de JWT. Um usuário administrador inicial é criado por variável de ambiente.
- **RF6 — Acesso por API key:** `GET /v1/items` e `POST /v1/collections` aceitam o header `X-API-Key`, configurado por variável de ambiente, para consumo por sistemas externos.
- **RF7 — Painel Vue:** telas de login, lista de itens com filtros, detalhe do item, disparo manual de coleta e lista de coletas agendadas.
- **RF8 — Documentação:** Swagger/OpenAPI em `/swagger-ui.html` e `/v3/api-docs`.

## 4. Requisitos não funcionais

- Java 21, Spring Boot 3.4.x, Maven, JUnit 5.
- PostgreSQL como banco; Flyway para as tabelas do hub.
- Frontend em Vue 3 em projeto separado, dentro deste repositório, em `frontend/`.
- Segredos (senha do admin, chave de API, segredo do JWT, credenciais do banco) só por variável de ambiente. Nada no Git.
- Respostas paginadas com limite máximo de 100 itens por página.
- Coletas agendadas respeitam os limites e as regras de cada fonte já definidos nos coletores. O hub não contorna bloqueios de fonte.
- Coleta sob demanda e agendada usam a mesma regra de deduplicação do `collector-runner-mini`.

## 5. Modelo de dados

Lido do `collector-runner-mini` (somente leitura pelo hub):
- `collected_item`: fonte, id externo (único por fonte), título, link original, data de publicação e data da coleta.

Criado pelo hub (Flyway no hub):
- `app_user`: id, e-mail, hash da senha, papel.
- `scheduled_collection`: id, fonte, entrada, intervalo em minutos, ativo, data da última execução.

## 6. Autenticação

- Painel: JWT emitido após login, expiração curta.
- API: header `X-API-Key` com comparação em tempo constante.
- Rotas de escrita (`POST /v1/collections`, cadastro de agendamentos) exigem JWT ou API key.

## 7. Implantação

- **Fase 1:** Docker Compose local com PostgreSQL, backend e frontend.
- **Depois:** deploy no Railway. A autenticação precisa estar ativa antes de qualquer deploy público; esse é um bloqueio registrado nesta spec.

## 8. Fora de escopo desta fase

- Alertas de novidade (e-mail, Telegram).
- Multiusuário com papéis além de administrador.
- Chamadas HTTP entre os projetos.
- Mensageria e fila.
- Coleta de fontes ainda não permitidas (Issuu, Reddit sem aprovação).

## 9. Critérios de aceite

- Listar, filtrar e detalhar itens funciona com dados gravados pelo `collector-runner-mini`.
- Uma coleta disparada pela API grava itens no banco e devolve a contagem.
- Uma coleta agendada executa no intervalo configurado e registra a data da última execução.
- Sem JWT ou API key válidos, as rotas protegidas respondem 401.
- Painel Vue faz login, lista itens com filtros e dispara uma coleta.
- Swagger publicado e acessível.
- Testes passam com `mvn test` e com `npm run build` no frontend.

## 10. Perguntas em aberto

1. O agendador fica no hub ou no `collector-runner-mini`? Esta spec assume o hub.
2. Qual o intervalo mínimo permitido para coletas agendadas?
3. Quantos dias os itens ficam guardados? Esta spec assume retenção indefinida até definirmos retenção e exclusão, como pede a spec do coletor.
4. Quem pode cadastrar agendamentos? Esta spec assume só o administrador.

## 11. Fases

- **Fase 1 (esta spec):** backend com API, autenticação, agendamento e Swagger; painel Vue; Docker Compose local.
- **Fase 2:** deploy no Railway, depois de revisar a autenticação e as perguntas em aberto.
