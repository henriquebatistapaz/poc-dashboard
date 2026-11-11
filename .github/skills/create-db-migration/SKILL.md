---
name: create-db-migration
description: Create a database schema migration the right way (Flyway/Liquibase or equivalent). Use whenever a task requires adding/changing tables, columns, indexes or constraints — schema changes NEVER go directly to the database or only into ORM entities.
---

# Create a database migration

A schema change that isn't a migration doesn't exist: it won't reach other environments and will silently diverge dev from prod.

## 1. Discover the migration setup

Find the tool and conventions in the project: Flyway (`db/migration/V*__*.sql`), Liquibase (`db/changelog/`), or other (Alembic for Python, etc.). Read the 2-3 most recent migrations — naming, style and idempotency conventions come from there, not from generic docs. No migration tool in the project? STOP: introducing one is an SDD, not a side effect of your task.

## 2. Write the migration

- **Next version number / timestamp** per the convention found; descriptive name (`V42__add_status_to_orders.sql`).
- **Never edit an already-applied migration** — checksums will break every environment. Fixing a bad migration = a new migration.
- Pair every schema change with the ORM entity change (both directions must match — use `inspect-postgres-schema` to verify current reality first).
- Prefer **expand/contract** for anything on tables with live traffic:
  1. Expand: add the new column/table (nullable / with default), deploy code that writes both
  2. Migrate data (batched `UPDATE`s, not one giant transaction)
  3. Contract: only after all code reads the new shape, a later migration drops the old
- Destructive operations (`DROP`, column removal, type narrowing) get their own migration, shipped at least one release after the code stopped using them.

## 3. Landmines checklist

- `NOT NULL` on an existing table needs a default or a backfill step first
- New index on a big table: check if the tool/DB supports non-blocking creation (`CREATE INDEX CONCURRENTLY` — note: not inside a transaction in Postgres)
- Renames are drop+add in disguise for running code — treat as expand/contract
- Data migrations mixed with DDL in one file: separate them
- Migration must run on an empty database too (CI) — no assumptions about existing data

## 4. Validate

Run the migration against a disposable local database (docker-compose/Testcontainers) from zero AND from the previous version. Both must pass before the MR.

## Safety rules

- Never apply schema changes manually to any shared environment "to test".
- Rollback strategy stated in the MR/SDD — even if it's "roll forward only", say so explicitly.
