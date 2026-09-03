# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the
codebase.

## Before exploring, read these

- **`docs/CONTEXT.md`** — the glossary. Note the path: it is **not** at the repo root, where the
  skills' default convention would look for it.
- **`docs/adr/`** — four independently-numbered sets, each with its own index, under a root
  `ADR-INDEX.md`: `api/`, `architecture/`, `frontend/`, `testing/`. Read the sets that touch the
  area you are about to work in.

There is no `CONTEXT-MAP.md`, and this is a **single-context** repo. The four ADR sets are a
numbering split, not a context split: there is one glossary and one ubiquitous language.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest
creating them upfront. The `/domain-modeling` skill creates them lazily when terms or decisions
actually get resolved.

## File structure

```
/
├── AGENTS.md
├── docs/
│   ├── CONTEXT.md                     ← the glossary
│   ├── agents/                        ← these files
│   └── adr/
│       ├── ADR-INDEX.md               ← which set a decision belongs in
│       ├── api/ADR-INDEX.md           ← ADR-001…  numbered within this set
│       ├── architecture/ADR-INDEX.md  ← ADR-001…  numbered independently
│       ├── frontend/ADR-INDEX.md      ← ADR-001…  numbered independently
│       └── testing/ADR-INDEX.md       ← ADR-001…  numbered independently
└── services/{api,frontend,nginx,listmonk,mailserver}/
```

## Always cite an ADR with its set

Numbering restarts per set, so **four different decisions are called `ADR-001`**. "frontend
ADR-002" is unambiguous; "ADR-002" is not. `docs/adr/ADR-INDEX.md` states the numbering policy
and which set a decision belongs in — an architecture decision governs how the service is
structured or how work is executed; a testing decision binds both stacks or governs a layer that
spans them; a decision that only ever affects one stack stays in that stack's set.

## Use the glossary's vocabulary

`docs/CONTEXT.md` is unusually prescriptive and says so: it names the synonyms to **avoid**, not
only the terms to use. When your output names a domain concept — an issue title, a refactor
proposal, a hypothesis, a test name, a CSS class — use the glossary's term.

Two rules bite constantly:

- **Shared band names.** A shared component on the island is named, inside and out, for the
  shape it draws, never for the domain that first needed it: `slice`, not `team-slice`; `stop`,
  not `season`. This governs scoped CSS and local variables, not just the public surface.
- **Number, not ID.** A board is identified by its **number** in urls and to readers. That is a
  different value from its database key, and the distinction is deliberate.

Also: **board photo** (the group photograph of the whole board) versus **portrait** (one
member's picture) — never "avatar", "headshot" or "group shot"; **nickname** (the membership's
own, not the person's); **in office** (derived from dates, never a flag).

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing
language the project doesn't use (reconsider) or there's a real gap (note it for
`/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently
overriding:

> _Contradicts testing ADR-001 (test pyramid and layer placement), but worth reopening because…_
