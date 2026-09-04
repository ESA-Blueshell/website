## Agent skills

### Issue tracker

Issues, specs and tickets live as GitHub issues in `ESA-Blueshell/website`, via the `gh` CLI.
See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical roles, each label named for its role. See `docs/agents/triage-labels.md`.

### CI

`Validate` runs the whole suite on **pull requests**, not on branch pushes, and check-runs
belong to a sha rather than to a branch. See `docs/agents/ci.md`.

### Domain docs

Single-context, but the glossary is at `docs/CONTEXT.md` rather than the repo root, and ADRs
are split into four independently-numbered sets. See `docs/agents/domain.md`.
