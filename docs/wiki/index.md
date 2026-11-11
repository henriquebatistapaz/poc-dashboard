# Dashboard Mini — Knowledge Wiki

Base de conhecimento curada para humanos e agentes. Fatos estruturais estão no código; esta wiki guarda o que o código não expressa: **por que** as decisões foram tomadas, regras de negócio, fluxos e pegadinhas.

Segue o [padrão LLM-wiki do Karpathy](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f): este índice liga todas as páginas por wikilinks, agrupadas por categoria.

## Architecture

- [[architecture-overview]] — visão geral e fluxo principal
- [[integracao-runner-embutido]] — como as bibliotecas irmãs entram no dashboard

## Domains

- [[dominio-coleta-e-itens]] — coleta, deduplicação, remoção e bloqueio de reingestão
- [[dominio-autenticacao]] — admin por configuração, JWT e API key

## Decisions

- (nenhuma página ainda)

## Operations

- [[ops-deploy-railway]] — deploy da API e do painel no Railway

---

## Rules for this wiki

- Uma página por tópico, nome em kebab-case igual ao wikilink.
- Toda página listada aqui — página fora do índice não existe.
- **Doc drift é bug**: se uma mudança deixa uma página errada, atualizar a página faz parte da mudança.
- Prefira diagramas mermaid a prosa para fluxos e dependências.
