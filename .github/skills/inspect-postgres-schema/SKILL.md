---
name: inspect-postgres-schema
description: Inspect the real schema of a PostgreSQL database (tables, columns, types, indexes, foreign keys) before writing entities, migrations, repositories or SQL. Use whenever a task involves reading or changing data in Postgres and the schema is not already known from the code.
---

# Inspect PostgreSQL schema

Never guess table or column names — inspect first, then code against facts.

## 0. A read-only MCP server first (optional)

If the project defines a `postgres` server in `.mcp.json`, `.vscode/mcp.json` or `.cursor/mcp.json` (set up by `/ai-init`, read-only by its credential) and its tools are available in this session, inspect through it — nothing to paste, nothing to install. Not configured, not available, or it fails → continue with step 1 below, unchanged (`psql`).

## 1. Find the connection (never hardcode)

Look for the connection config in the project, in this order: environment variables (`DATABASE_URL`, `SPRING_DATASOURCE_*`, `PG*`), local config files (`application-local.properties`, `.env`), `docker-compose.yml` (service `postgres`). Use those values with `psql`; if only production credentials are found, STOP and ask the user for a dev/local target.

```bash
psql "$DATABASE_URL" -c "..."
# or
PGPASSWORD=... psql -h <host> -p 5432 -U <user> -d <db> -c "..."
```

## 2. Inspect

```sql
-- Tables (with sizes, helps spot the important ones)
SELECT schemaname, tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema');

-- Columns of a table: name, type, nullability, default
SELECT column_name, data_type, udt_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = '<table>' ORDER BY ordinal_position;

-- Indexes (includes partial/expression indexes psql \d hides less clearly)
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = '<table>';

-- Foreign keys in/out of a table
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid = '<table>'::regclass OR confrelid = '<table>'::regclass;

-- Enums and custom types (udt_name above pointing to them)
SELECT t.typname, e.enumlabel FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid ORDER BY t.typname, e.enumsortorder;
```

Shortcut when psql meta-commands are available: `\dt`, `\d+ <table>`, `\dT+`.

Pay attention to non-obvious column types: `jsonb` (inspect real shape with `SELECT jsonb_pretty(col) FROM t LIMIT 3`), `vector` (pgvector — note the dimension), arrays, custom enums.

## 3. Use the facts

- Map entity fields to the exact column names/types found — flag mismatches with the existing ORM entities instead of silently "fixing" either side.
- New columns/tables → belong in a migration (Flyway/Liquibase/etc. per AGENTS.md), never applied manually.

## Safety rules

- Read-only: only `SELECT` and catalog queries. Never `INSERT/UPDATE/DELETE/DDL` during inspection.
- Sample data may contain PII — look at it to infer shape, but do not paste row contents into files, specs or commits.
- Production databases: only with explicit user confirmation, and still read-only.
