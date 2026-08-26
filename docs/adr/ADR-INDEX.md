# Architecture Decision Records Index

Architectural decisions are split into separate sets: two per stack, plus one
cross-cutting set for decisions that bind both.

## ADR Sets
- [API ADR index](api/ADR-INDEX.md)
- [Frontend ADR index](frontend/ADR-INDEX.md)
- [Testing ADR index](testing/ADR-INDEX.md)

## Numbering Policy
- API ADRs are numbered within `docs/adr/api`.
- Frontend ADRs are numbered independently within `docs/adr/frontend`.
- Testing ADRs are numbered independently within `docs/adr/testing`. A decision
  belongs to this set when it binds both stacks, or when it governs a test layer
  that spans them. A decision that only ever affects one stack stays in that
  stack's set.
- New ADRs must be added to the appropriate set and index.

## Status Definitions
- **Proposed**: Under discussion
- **Accepted**: Approved and implemented
- **Deprecated**: No longer recommended
- **Superseded**: Replaced by another ADR

Accepted records a decision, not its enforcement. Where a decision is agreed but
the tooling that enforces it has not landed, the ADR carries an
**Implementation status** section naming the gap.

## Related Documentation
- [Flow documentation](../flows/README.md)
- [CLAUDE.md](../../CLAUDE.md)
- [AGENTS.md](../../AGENTS.md)
