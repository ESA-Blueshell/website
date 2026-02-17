# Agent Guide

This file is synchronized with the root `AGENTS.md` and `CLAUDE.md`.

## Primary References
- `AGENTS.md`
- `CLAUDE.md`
- `docs/adr/ADR-INDEX.md`
- `docs/adr/api/ADR-INDEX.md`
- `docs/adr/frontend/ADR-INDEX.md`

## Agent Expectations
- Make minimal, focused, behavior-preserving changes unless explicitly asked otherwise.
- Follow domain and layer boundaries in both API and frontend code.
- Keep generated API clients as infrastructure; map to domain models at boundaries.
- Run or propose relevant verification commands and report outcomes clearly.
