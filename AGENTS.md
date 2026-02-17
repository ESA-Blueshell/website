# AGENTS.md

This guide is aligned with `CLAUDE.md` and summarizes working rules for AI agents in this repository.

## Project
- Full-stack application: Kotlin/Spring API + Vue 3 frontend.
- API architecture follows domain-driven, layered boundaries enforced by ArchUnit tests.
- Frontend architecture follows domain/feature boundaries with explicit API adapter mappings.

## Core Commands

### API
```bash
docker compose -f docker-compose.dev.yml up api
docker compose -f docker-compose.dev.yml run api ./gradlew :api:test
```

### Frontend
```bash
cd frontend
yarn install
docker compose -f docker-compose.dev.yml up frontend
yarn lint
yarn typecheck
yarn build
```

## ADR References
- Umbrella index: `docs/adr/ADR-INDEX.md`
- API ADRs: `docs/adr/api/ADR-INDEX.md`
- Frontend ADRs: `docs/adr/frontend/ADR-INDEX.md`

## Working Rules
- Respect layer and domain boundaries.
- Keep mappings at boundaries explicit and testable.
- Avoid direct coupling between frontend components and generated transport models.
- Update documentation and tests when behavior or architecture changes.
- Prefer focused, reviewable changes over broad rewrites.

## Source Of Truth
- Primary detailed guidance: `CLAUDE.md`
- This file should stay consistent with `CLAUDE.md`.
