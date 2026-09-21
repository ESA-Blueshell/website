# ADR-026: The Schema Starts From a Baseline, Owned by Liquibase

**Status:** Accepted
**Supersedes:** [ADR-010: Database Migrations with Flyway](ADR-010-database-migrations-with-flyway.md)

## Context

Flyway ran at api boot, so a schema change was discovered pod by pod rather than gated before any pod served traffic. Moving migrations into a step that runs once per release means the runner is no longer the application, and a runner that is not the application cannot execute the Kotlin migrations the tree carried.

Flyway's undo is a paid feature, so no rollback was ever authored and none could be. The 96 migrations had also become the only way to build a schema: every test run replayed all of them, and the MariaDB 10.11 pin exists because 11.x tightened DDL rules that V28 relies on.

## Decision

Liquibase owns the schema, starting from a baseline.

The 96 migrations are **not ported**. The schema they produce is captured once as `db/changelog/baseline/baseline.sql` and becomes changeset one. A database that already ran them is marked as having the baseline rather than applying it; an empty one applies it.

Changes after the baseline are **YAML changesets** under `db/changelog/changes/`. Structured changeTypes are what let the expand/contract check read a changelog rather than pattern-match SQL, and they carry to PostgreSQL if that decision is ever taken. Raw SQL remains available for what changeTypes cannot express.

**Every changeset carries a rollback.** A standard changeType has one derived for it; a raw-SQL changeset must declare `--rollback` explicitly, because Liquibase derives nothing for SQL it did not generate and `rollback` fails on the first changeset that has none. The baseline itself is raw SQL, so its rollback is written out: drop the 57 tables with foreign-key checks off, and drop each trigger in the changeset that created it.

Authoring a rollback is not the same as running one unattended. A down migration that executes after the new code has written rows the old schema cannot hold loses those rows, so a rollback is a rehearsed manual procedure with a backup, not an automatic response to a failed release.

**A changeset must be readable by the release before it.** The migration runs before any new pod serves, so the version already in production meets the new schema first and keeps serving against it through the whole canary analysis. Dropping or renaming a column, or adding a constraint the old rows do not satisfy, breaks what is serving — and reverting the release does not undo it, because the schema is never reverted automatically.

So a removal takes two releases. The first stops reading the column and ships. The second drops it, once nothing in production maps it. Taking both at once is how 1.8.0 came to drop `boards.image` while v1.7.1 still selected it (#1365).

`scripts/check-changeset-compatibility.py` refuses the breaking changeTypes on a pull request that adds a changeset, reading the YAML rather than the SQL it generates. It does not read `rollback:`, where a `dropTable` undoing a `createTable` is ordinary, and it allows a constraint on a table the same changelog creates, which no released version knows about. A change that genuinely is safe is labelled `schema:breaks-the-previous-release`, which turns the refusal into a warning that stays visible on the pull request.

## Consequences

**The migration history leaves the tree.** It stays in git, but the schema's origin is now the baseline. `scripts/check-migration-order.sh` and ADR-010's immutability rule both stop applying and are removed.

**Nothing replays V28 any more**, so the MariaDB 10.11 pin can be revisited on its own merits.

**Tests bootstrap through Liquibase** against the same service container, and one changeset replaces a 96-file replay.

**Two things the baseline had to be built around**, recorded because they are not obvious and will recur if it is ever regenerated:

- A schema dump is alphabetical, so a foreign key routinely names a table further down the file. The baseline disables `FOREIGN_KEY_CHECKS` for its first changeset and restores it at the end.
- Liquibase treats `endDelimiter` as a regular expression, where the conventional `$$` matches nothing and every trigger arrives as one statement. Each of the nine triggers is therefore its own changeset with `splitStatements:false`, which requires no agreement about where a statement ends.

**A test JVM carries the changelog twice** — once in the project jar, once in `build/resources/main` — and Liquibase refuses an ambiguous path where Flyway scanned locations. Test tasks set `LIQUIBASE_DUPLICATE_FILE_MODE=WARN`; production runs the jar alone and never sees the duplicate.

## Applying this to a database that ran the migrations

The baseline describes what such a database already has, so it must be recorded as applied rather than run:

```bash
liquibase --changeLogFile=db/changelog/db.changelog-master.yaml changelog-sync
```

Rehearse against a restored dump first. If production has drifted from what the migrations produce, `changelog-sync` records the baseline as applied without reconciling that difference — the drift becomes invisible rather than resolved. Compare a dump of production against a database built from the baseline before running it anywhere that matters.
