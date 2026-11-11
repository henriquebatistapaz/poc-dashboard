# SDD: migracao-de-social-posts

**Complexity:** S

> Status: Approved. Script de importação local implementado; não executado em banco compartilhado.

## 1. Contexto
O runner standalone mantém o histórico em `social_posts`; o dashboard usa `collected_item`. O destino preserva fonte, identidade externa, título/link e data, mas não tem campo de autor.

## 2. Objetivo
Disponibilizar importação única e repetível do histórico, sem sobrescrever itens já coletados pelo dashboard.

## 3. Escopo
### 3.1 Incluído
- Script SQL PostgreSQL para tabelas `public.social_posts` e `public.collected_item` no mesmo banco.
- Validação de comprimentos/campos obrigatórios antes de copiar.
- Inserção idempotente por `(source, external_id)`.

### 3.2 Fora de escopo
- Cópia entre bancos PostgreSQL distintos.
- Preservar `author`, que não existe no modelo de `collected_item`.
- Execução automática no deploy ou remoção da tabela de origem.

## 4. Requisitos
- RF-01: Itens históricos compatíveis são inseridos em `collected_item`.
- RF-02: Conflitos de `(source, external_id)` preservam o registro existente.
- RF-03: Linha sem permalink usa URL determinística sob `legacy.invalid`.
- RNF-01: Linhas incompatíveis abortam a transação antes da importação.
- RNF-02: Reexecutar o script não cria duplicados.

## 5. Arquitetura e desenho
Executar `scripts/migrate-social-posts.sql` com `psql` conectado ao banco que contém as duas tabelas. A transação verifica existência e compatibilidade; então usa `INSERT ... ON CONFLICT DO NOTHING`. O texto vira título limitado a 1000 caracteres, a data é preservada e o permalink é usado como link. `legacy.invalid` identifica links sintéticos que exigem revisão.

## 6. Plano de implementação
1. Adicionar script transacional, compatível com o schema Flyway do dashboard.
2. Documentar pré-requisito de tabelas co-localizadas e perda do campo author.
3. Revisar estaticamente nomes, limites e chave única.

## 7. Critérios de aceite
- AC-01: Script valida a presença das duas tabelas e faz rollback em incompatibilidade.
- AC-02: Conflitos ignorados pela chave única tornam a operação idempotente.
- AC-03: `author` nunca é copiado para campo incorreto.

## 8. Operação
Faça backup e revisão prévia do banco; execute explicitamente apenas depois de confirmar que as tabelas pertencem à mesma base. A tabela original não é alterada. Linhas sem permalink recebem URL sintética e devem ser reconhecidas como tal nos consumidores.

## 9. Riscos e mitigação
- Se a origem estiver em outra base, o script interrompe sem copiar; seria necessária uma etapa de exportação/importação revisada.
- O modelo de destino não preserva autor e pode truncar texto acima de 1000 caracteres.
- Dados existentes em conflitos não são atualizados pelo histórico legado.

## 10. Rollback
O script não altera nem remove `social_posts`. Linhas importadas podem ser identificadas pela origem e removidas somente com uma operação de rollback explicitamente revisada.

## 11. Verificação
Revisão estática do script concluída; execução PostgreSQL não feita nesta rodada, pois exige acesso ao banco com as tabelas de origem e destino.
