# ADR-005: Frontend Coverage Parity

## Status
Accepted

## Context

The frontend has no coverage gate worth the name. `vitest.config.ts` declares
`thresholds` for **six named files**, one of which — `AddressForm.vue` at 45%
branches — is a floor set below the level it was already at. There is no global
gate, and the e2e run collects istanbul coverage that nothing checks.

Measured at `b4a0ca80`:

| Metric | Covered / total | % |
|--------|-----------------|---|
| Functions | 1357 / 2543 | 53.4 |
| Branches | 2486 / 5017 | 49.6 |
| Lines | 3584 / 5726 | 62.6 |

103 of 165 files fail a 100%-functions rule, 87 fail 80% branches, and 35 have zero
coverage — including all of `src/domains/cohorts`, `EmailManager.vue` (0/77
functions) and `CohortSubjectDetail.vue` (0/61).

Applying the API's rules unchanged would be neat. Whether the frontend harness can
*measure* what the rules ask for is the question this ADR has to answer first,
because a gate on numbers the tooling cannot produce is worse than no gate.

## Decision

**The same rules, the same dates: 100% functions, 80% branches rising to 100%,
scoped to changed files.** `functions` is the frontend's METHOD counter; the
ratchet dates in
[ADR-003](ADR-003-coverage-counters-thresholds-and-ratchet.md) apply unchanged.

Symmetry is the point. One numeric policy, stated once, is what stops the two
stacks drifting into separate standards, and the existing six per-file thresholds
are superseded by it.

Two harness defects must be fixed first. Both are prerequisites, not caveats.

### Vuetify must actually render

`tests/setup.ts` never calls `createVuetify()`. It installs a plain `$vuetify`
mock object and its `warnHandler` suppresses `"Failed to resolve component:"`, so
every Vuetify component silently fails to resolve. Vuetify's own
[testing guide](https://vuetifyjs.com/en/getting-started/unit-testing/) requires the
plugin be installed.

What that costs was measured directly rather than argued from documentation. A probe
mounting an unresolved `<v-dialog>` and `<v-menu>` produced:

```
HTML: <div> <v-dialog modelvalue="false"></v-dialog> <v-menu></v-menu> </div>
DEFAULT_SLOT_EVALUATED=true
SCOPED_SLOT_EVALUATED=false
```

Two distinct failures, and the second is the worse one:

- A **default slot** is evaluated, so its slot function counts as covered — but the
  element renders **empty**, so the children never reach the DOM. A `@click` handler
  inside is created and never invoked; its body stays uncovered and no test can
  click it.
- A **named or scoped slot** is never evaluated at all. Everything inside
  `#activator` on a `v-menu`, or `#item.name` on a `v-data-table`, is invisible to
  the suite — handler and slot function alike.

Consistent with this, **all 17** files combining a Vuetify slot with an event
handler currently fail the functions rule. Until `createVuetify()` is installed and
the warning suppression removed, 100% functions is not difficult on these files but
impossible, and any number reported for them is misleading.

### Branch coverage must come from istanbul

The `v8` provider miscounts branches in Vue SFC templates: AST remapping emits
`NaN` in lcov `BRDA` entries ([vitest#9725](https://github.com/vitest-dev/vitest/issues/9725)),
where istanbul produces integers across the same suite. A branch gate reading `NaN`
is not a gate. The unit suite therefore moves to `provider: "istanbul"`.

### Scoping, and what not to use for it

Changed-file scoping is applied by generating threshold keys for the files in the
diff. It is explicitly **not** implemented with vitest's `--changed` flag: that flag
selects which *tests* run, so coverage is computed only over what those tests
happened to import, and a threshold read from such a run is meaningless
([vitest#5237](https://github.com/vitest-dev/vitest/issues/5237)).

`coverage.thresholds.autoUpdate` is likewise not used. With `perFile: true` it
raises thresholds from the global percentage rather than the worst file
([vitest#3179](https://github.com/vitest-dev/vitest/issues/3179)), which
manufactures failures on the following run.

## Implementation status

Decided, not yet enforced. Sequenced, because each step depends on the one before:

1. Install `createVuetify()` in `tests/setup.ts`, drop the `"Failed to resolve
   component:"` suppression, and add the `transition` and `teleport` stubs that
   `v-dialog` and `v-menu` need under jsdom. Existing tests asserting against
   unrendered markup will fail and must be fixed — that is the defect surfacing,
   not a regression.
2. Switch the unit suite to `provider: "istanbul"` and re-baseline. The numbers
   above are v8 numbers and will move.
3. Replace the six per-file thresholds with diff-scoped thresholds.

Step 1 also repairs a second problem: jsdom render benchmarks currently measure
stubs rather than components, so their results have never meant anything.

## Consequences

### Positive
- **One numeric policy across both stacks**, with one ratchet and one place to
  amend it.
- **The harness fix is worth more than the gate.** Components that have never truly
  rendered in a unit test start doing so, which makes a whole class of behaviour
  testable for the first time.
- **The `AddressForm.vue` floor of 45% branches disappears** along with the rest of
  the per-file list — a floor set beneath the current level is a ratchet pointing
  the wrong way.

### Negative
- **Step 1 will turn the suite red.** Tests written against components that
  rendered as empty elements were asserting on markup that is about to change.
- **jsdom mounts get slower** once Vuetify is real, and the unit suite is the fast
  feedback loop.
- **istanbul instrumentation is slower than v8**, paid on every run.
- **The starting point is far from the target.** 103 of 165 files fail today. Diff
  scoping means that is not a blocker, but every touched file becomes more
  expensive.

### Neutral
- **jsdom still cannot compute layout.** Anything depending on real dimensions —
  `v-data-table` virtual scroller heights in particular — remains unprovable at
  this layer and belongs in e2e regardless of what the gate says.

## Related ADRs
- [ADR-002: Coverage Gates Apply to Changed Code](ADR-002-coverage-gates-apply-to-changed-code.md) — the scoping applied here
- [ADR-003: Coverage Counters, Thresholds and the Ratchet](ADR-003-coverage-counters-thresholds-and-ratchet.md) — the numbers and dates
- [ADR-006: Frontend End-to-End Completeness](ADR-006-frontend-end-to-end-completeness.md) — the layer that proves what jsdom cannot
- [Frontend ADR-007: Testing and Quality Gates](../frontend/ADR-007-testing-and-quality-gates.md) — superseded by this set
