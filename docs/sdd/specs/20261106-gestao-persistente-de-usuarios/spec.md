# SDD: gestao-persistente-de-usuarios

**Complexity:** M

> Aprovada pelo usuário em 2026-11-11 e em implementação.

## 1. Context
Hoje há um único admin configurado por variável de ambiente.

## 2. Goal
Gestão de usuários persistente.

## 3. Scope
### 3.1 In scope
- Tabela de usuários e papéis.

### 3.2 Out of scope
- Cadastro público.

## 4. Requirements
### 4.1 Functional
- FR-01: Admin cria e desativa usuários.

### 4.2 Non-functional
- Papéis `ADMIN` e `OPERATOR`; sem cadastro público.

## 5. Architecture and Design
Flyway cria `user_account` com email único, hash BCrypt, papel, estado ativo e data de criação. O primeiro administrador é inserido da configuração de runtime somente se ainda não existir. Login consulta a tabela persistente; JWT do admin recebe privilégios de gestão, leitura, remoção e coleta. `OPERATOR` recebe leitura e coleta. Rotas admin-only criam, listam e desativam contas; o usuário autenticado não pode se desativar.

## 6. Implementation Plan
Adicionar migration V4, entidade/repositório, bootstrap idempotente do admin, autenticação pelo banco e API de gestão protegida.

## 7. Acceptance Criteria
- AC-01: Login funciona com usuário persistido, usuário desativado não autentica, somente admin pode gerir usuários e respostas não expõem hashes.

## 8. Observability and Operations
- Exigir senha de pelo menos 12 bytes para novas contas, armazenar BCrypt e não registrar senha ou hash.

## 9. Risks and Mitigations
- A definir.

## 10. Rollback
A definir.

## 11. References
- README (Pending)
