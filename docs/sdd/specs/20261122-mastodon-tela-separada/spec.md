# Spec: Mastodon (OAuth) em tela separada

**Complexity:** S
**Security-sensitive:** no (só muda a apresentação; a API, o token e as regras de acesso não mudam)
**Status:** Shipped
**Gate 1:** Aprovada pelo usuário em 2026-11-22 (chat: "separa em outra tela essa parte do mastodon! qualquer coisa faz em nova sdd spec!")

## 1. Context & objetivo

O card "Mastodon com sua conta" ficava na mesma página do painel de coleta, entre a coleta e o histórico. O usuário quer a integração com o Mastodon (poc-collector-oauth) em uma tela própria, separada do painel.

## 2. Escopo

### Incluído

- Uma tela "Mastodon" acessada pela barra lateral (e pela gaveta do celular), com a rota `#mastodon`.
- Na tela, somente o card de conexão, busca e publicação do Mastodon, com o título e a descrição da integração.
- O painel principal volta a mostrar só conteúdos, nova coleta e atividade.
- Textos nos três idiomas já existentes (`mastodon.title` e `mastodon.description`).

### Fora do escopo

- Mudar a API, o token, as regras de acesso ou a spec `20261122-integracao-mastodon-oauth`.
- Trocar o roteador por `vue-router`: o painel já navega por âncoras, e a mudança continua com `hashchange`.

## 3. Decisões

- **Rota por hash (`#mastodon`):** segue o padrão de navegação já usado pelo painel (`#library`, `#collect`, `#activity`). Sem nova dependência.
- **Visibilidade da seção:** `App.vue` mantém um estado `mastodonView`, sincronizado com o hash na montagem e a cada `hashchange`. Quando o hash é de outra seção, a página volta ao painel e rola até a âncora.

## 4. Critérios de aceitação

- **AC-01:** Sem o hash `#mastodon`, o painel mostra conteúdos, nova coleta e atividade, e não mostra o card do Mastodon. Evidência: `App.test.ts` > "shows the Mastodon connection on its own screen and back to the dashboard by hash".
- **AC-02:** Com o hash `#mastodon`, a página mostra só o card do Mastodon, e o item "Mastodon" da barra lateral fica ativo. Evidência: mesmo teste.
- **AC-03:** Voltar para uma seção do painel (`#library`) restaura o painel e rola até a seção. Evidência: mesmo teste.
- **AC-04:** O link "Mastodon" da gaveta mobile abre a tela e fecha a gaveta. Evidência: mesmo teste.
- **AC-05:** O gate de cobertura do frontend continua em 100% (statements, branches, functions, lines). Evidência: `npx vitest run --coverage` com saída 0.
- **AC-06:** Build (`vue-tsc` + `vite build`) e lint passam. Evidência: `npm run build` e `npm run lint` sem erro.

## 5. Validação

- Frontend: 54 testes verdes, cobertura 100%, build e lint verdes (2026-11-22).
- Não foi feito teste visual em 375px nesta entrega. A tela reaproveita o card já medido na spec `20261122-integracao-mastodon-oauth`.
