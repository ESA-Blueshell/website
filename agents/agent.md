# Agent Guide

This document defines how AI agents should work in this repository.

## Scope
- Agents may work on refactoring, documentation, bug fixes, features, and test work.
- Prefer minimal, reviewable changes with clear intent.

## General Best Practices
- Follow existing project structure and coding conventions.
- Keep changes small and composable; avoid unrelated edits.
- Favor deterministic tests and stable fixtures over timing-sensitive behavior.
- Preserve public APIs unless explicitly requested to change them.
- Remove or update obsolete docs and tests rather than leaving contradictions.

## Environment And Execution
- All code execution uses Docker Compose:
  - `docker compose -f docker-compose.dev.yml up SOME_SERVICE`
  - `docker compose -f docker-compose.dev.yml run SOME_SERVICE`
- System tests are to be generated using Playwright MCP directly on the host machine (no Docker Compose required).

## Testing
- All tests are run with Gradle.
- Unit and integration tests use JUnit.
- Architecture tests use ArchUnit.
- System tests use Playwright MCP and should generate Java system tests that validate current API state.
- Keep tests up to date with production behavior at all times.
- If tests are not run, state why and suggest the exact Gradle or Compose command to run them.

## Refactoring Guidelines
- Refactoring must preserve behavior.
- Write or update tests first to capture current behavior, ensure they pass before changes, and verify they pass after refactoring.
- Avoid mixing refactors with feature changes unless explicitly requested.
- Keep commits focused and reversible.

## Documentation Guidelines
- Include references for non-trivial claims or external behavior (links to source code paths, specs, or official docs).
- Keep docs accurate, concise, and task-focused.
- Prefer runnable examples that match current behavior.
- Avoid duplicating information across docs; link instead.
- Note assumptions, versions, and environment prerequisites.
- Update docs whenever behavior, configuration, or commands change.

## Output Expectations
- Provide a brief summary of changes and test coverage.
- Call out any behavior changes explicitly.
