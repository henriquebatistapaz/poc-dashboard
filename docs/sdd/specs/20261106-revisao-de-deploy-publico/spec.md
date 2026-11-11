# SDD: revisao-de-deploy-publico

**Complexity:** M

> Status: Approved. Revisão de código/checklist concluídos; inspeção dos valores no ambiente Railway production pendente.

## 1. Contexto
O dashboard é exposto por frontend nginx que encaminha chamadas à API. O Railway termina TLS na borda e injeta configuração de serviços; a revisão local não consegue atestar valores secretos do ambiente remoto.

## 2. Objetivo
Verificar autenticação, configuração, segredos e exposição antes de promover production.

## 3. Escopo
### 3.1 Incluído
- Revisão de autenticação JWT/API key, bootstrap do admin, CORS/CSRF, proxy e valores locais padrão.
- Hardening de validação de credenciais de bootstrap.
- Checklist operacional para inspeção antes de production.

### 3.2 Fora de escopo
- Ler ou alterar secrets do Railway.
- Fazer deploy, promover branch ou verificar dados reais em production.

## 4. Requisitos e resultados
- RF-01: Login exige configuração explícita de chave JWT; sem ela, login falha fechado.
- RF-02: Admin bootstrap aceita exatamente uma credencial (senha com 12–72 bytes ou hash BCrypt válido), e valida e-mail.
- RF-03: JWT tem escopo limitado e expira após 15 minutos; endpoints exigem as authorities correspondentes.
- RF-04: API key de leitura não autoriza escrita, administração ou remoção.
- RF-05: Frontend e API compartilham origem via nginx; autenticação usa bearer token stateless, sem cookie; CORS não é necessário e CSRF está desativado.
- RF-06: Checklist lista os valores e verificações que o operador deve confirmar no Railway antes da promoção.

## 5. Revisão técnica
- Não existe conta `admin/admin` hardcoded. O bootstrap era opcional, mas antes aceitava senha curta e valor sem formato BCrypt; foi endurecido.
- A API inicia sem JWT para modo leitura, usando chave de verificação efêmera; login não funciona sem chave configurada.
- `DASHBOARD_API_KEY` em branco não concede leitura. Quando usada, precisa ser aleatória e exclusiva por ambiente; comprimento não é validado pelo app.
- Configuração padrão de DB (`dashboard`/`dashboard`) e loopback é local. Railway deve definir explicitamente DB e `SERVER_ADDRESS=0.0.0.0`.
- O proxy aplica CSP, `nosniff`, `no-referrer` e `X-Frame-Options: DENY`.
- CORS não está habilitado: o frontend chama endpoints pela mesma origem.
- Valores do Railway production, políticas de acesso e logs remotos não foram inspecionados nesta rodada.

## 6. Critérios de aceite
- AC-01: Senha fraca, hash inválido ou configuração parcial de admin falham no bootstrap.
- AC-02: Checklist documentado no DEPLOY.md.
- AC-03: Testes de autenticação e autorização passam.
- AC-04: Operador revisa a configuração remota antes da promoção; esta etapa segue pendente.

## 7. Operação
Seguir a seção “Revisão de segurança antes de production” de `DEPLOY.md`; revisar valores sem copiá-los para logs, terminal compartilhado ou arquivos versionados. Manter Reddit desabilitado até aprovação oficial.

## 8. Riscos e mitigação
- Configuração remota fraca não pode ser detectada pelo código local; exigir inspeção operacional antes da promoção.
- API key não é validada em comprimento pelo app; gerar pelo menos 24 bytes aleatórios conforme procedimento operacional e limitar acesso ao secret store.
- Senhas bootstrap com menos de 12 bytes agora impedem início, exigindo atualização coordenada dos ambientes.

## 9. Rollback
Corrigir as variáveis de ambiente; não há mudança de schema. A validação estrita de credenciais exige valor válido para iniciar bootstrap.

## 10. Verificação
- Revisão estática de `SecurityConfiguration`, JWT, bootstrap, nginx, `.env.example` e DEPLOY.md.
- Testes unitários novos cobrem senha curta, hash inválido e configuração parcial.
- `mvn -B test`: 48 testes passaram, sem falhas/erros, em 2026-11-11.
- Railway production, browser e deploy não foram verificados.
