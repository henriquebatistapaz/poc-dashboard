# SDD: agendamento-de-coletas

**Complexity:** M

> Status: Approved. Implementação local em andamento.

## 1. Contexto
Coletas sob demanda não permitem definir execução futura. A fila pode usar `collection_run`, sem worker separado.

## 2. Objetivo
Permitir que um usuário autenticado agende uma coleta única e acompanhe seu resultado no histórico.

## 3. Escopo
### 3.1 Incluído
- Agendamento de coleta única em `collection_run`.
- Dispatcher periódico com reivindicação atômica dos itens vencidos.
- Recuperação de execuções agendadas interrompidas por reinício.
- Proteção da entrada do feed armazenada no banco.

### 3.2 Fora de escopo
- Recorrência, edição ou cancelamento de agendamentos.
- Worker separado ou promessa de execução em horário exato.

## 4. Requisitos
### 4.1 Funcionais
- RF-01: A API autenticada aceita `source`, `input` e `scheduledAt` futuro.
- RF-02: A data deve estar dentro dos próximos 365 dias.
- RF-03: Agendamentos vencidos são executados uma vez e têm status/resultados registrados no histórico.
- RF-04: Após reinício, agendamento ainda pendente permanece elegível; execução agendada interrompida e ainda protegida volta a pendente.

### 4.2 Não funcionais
- RNF-01: Agendamento fica desabilitado se `DASHBOARD_SCHEDULE_KEY` não estiver configurada.
- RNF-02: A chave contém 32 bytes codificados em 64 caracteres hexadecimais; a entrada é cifrada com AES-GCM antes de persistir.
- RNF-03: A entrada cifrada é removida quando a execução termina.
- RNF-04: O intervalo do dispatcher é configurável por `DASHBOARD_SCHEDULE_POLL_MS` (padrão 5000 ms).

## 5. Arquitetura e desenho
- Novas colunas `scheduled_at` e `encrypted_input` na migração Flyway V5.
- `POST /v1/collections/schedules` valida e cria execução PENDING.
- `CollectionScheduleDispatcher` consulta lotes vencidos e a atualização condicional de status reivindica cada execução uma única vez entre instâncias concorrentes.
- A execução usa o executor de coleta existente; sucesso ou falha termina no histórico padrão.
- `CollectionRunRecovery` recoloca execuções agendadas RUNNING em PENDING se a entrada cifrada ainda está disponível; execuções imediatas interrompidas são marcadas FAILED.

## 6. Plano de implementação
1. Adicionar configuração e cifra autenticada da entrada.
2. Persistir horário e entrada cifrada e expor o endpoint autenticado.
3. Despachar execuções vencidas e recuperar agendamentos após reinício.
4. Cobrir cifra, validação, execução, reinício e API por testes.

## 7. Critérios de aceite
- AC-01: Agendamento válido aparece no histórico e é executado após vencer.
- AC-02: Datas passadas, nulas ou acima de 365 dias são rejeitadas.
- AC-03: Sem chave configurada, endpoint responde serviço indisponível.
- AC-04: A entrada em texto claro não é persistida; a cifra pode ser decifrada apenas com a chave correta.
- AC-05: Reivindicação impede execução duplicada e o reinício não perde agendamento elegível.
- AC-06: Testes unitários e de API passam.

## 8. Observabilidade e operação
- O histórico padrão de `collection_run` informa horário, status e contagens.
- Configure a chave estável e secreta em todos os nós; trocar ou perder a chave torna os agendamentos ainda pendentes ilegíveis.
- Falha de coleta registra mensagem sanitizada, preservando segredo de feed.

## 9. Riscos e mitigação
- Precisão do horário depende do intervalo de polling e disponibilidade do processo; não é execução em tempo real.
- Chave perdida impede a descriptografia de agendamentos pendentes; manter backup seguro e estável da configuração.
- Várias instâncias podem consultar os mesmos IDs, mas a reivindicação condicional impede submissão duplicada.

## 10. Rollback
Desabilitar a chave interrompe criação/execução; manter as colunas aditivas no schema evita perda das execuções existentes. Reverter código sem preservar a migração exige deploy coordenado de banco.

## 11. Verificação local
- `mvn -B test`: 45 testes passaram, sem falhas, em 2026-11-11.
- PostgreSQL e concorrência entre processos não foram verificados nesta rodada.
