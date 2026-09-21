# ADR-007: Every Changed Line Runs Under a Unit Test

## Status
Accepted

## Context

[ADR-002](ADR-002-coverage-gates-apply-to-changed-code.md) decided that gates
bind the code a change touches and left three pieces unbuilt, so nothing was
enforced. [ADR-003](ADR-003-coverage-counters-thresholds-and-ratchet.md) set a
dated schedule for whole-codebase floors that has not started either. In the
meantime the only coverage rules that run are `minimum = "0.35"` on api
instructions, `0.40` on integration, and six per-file thresholds in
`vitest.config.ts`. None of them notices a new untested file.

The pull request report now carries a patch-coverage number, and a push to main
caches a baseline, so the number a gate would read is already computed on every
run.

## Decision

**A pull request cannot merge while a changed line that the unit suites measure
never ran.** The floor is 100% and the check is `Changed lines are covered` in
Validate. It blocks through `Validate complete`, the one required check in the
`main` ruleset, which refuses any job that neither passed nor skipped.

### Measured by the unit suites, and nothing else

`services/api` unit coverage and `services/frontend` unit coverage. Not
integration, not e2e, not the system suite. Those layers prove the wiring, and a
line credited to them is a line whose behaviour may still be unasserted: the
slowest test in the repository would be the one keeping the gate green. Coverage
is reported the same way, so the number in the comment is the number in the
gate.

The cost is visible and accepted: an api line whose only exercise today is an
integration test reads as uncovered, and the pull request that touches it writes
a unit test or moves the logic somewhere a unit test can reach. That is the
intended pressure, not a side effect.

### Measured, not merely changed

A line counts only when a coverage report carries a record for it. A comment, a
blank line, a docs change, a workflow file and anything outside an `include`
list are absent from the denominator rather than counted against it. A pull
request that changes no measured line passes with nothing to say.

### 100%, not a percentage to argue about

Any floor below 100% is a budget for untested lines, and a budget gets spent.
At 100% the only conversations are "write the test" and "this line should not
exist", both of which are about the change in front of the reviewer.

## Alternatives

| option | cost if taken | why rejected |
|---|---|---|
| Count every suite's coverage | Almost no pull request fails | A Playwright run that walks a page credits every line it touches, and the gate stops meaning anything |
| A floor below 100%, say 90% | One in ten changed lines needs no test | The slack is what nobody decides; it fills up |
| Report only, block later | Nothing is blocked while the habit forms | The report already exists and changed nothing; the number has been visible for weeks |
| A `coverage-exempt` label | An escape hatch for the genuinely awkward case | An exemption that costs one label is taken by default; an admin override is rarer and leaves the same audit trail |

## Consequences

### Positive
- A new file with no test cannot arrive. This is the failure mode the existing
  floors cannot see.
- The gate reads the same reports as the comment, so a failure is already
  explained by the table above it, line by line.

### Negative
- **Moved code reads as new**, so extracting untested code fails the gate. This
  is [ADR-002](ADR-002-coverage-gates-apply-to-changed-code.md)'s documented
  failure mode and it is paid by whoever does the extraction.
- **Integration-only code costs more to touch.** A one-line fix to a class only
  the integration suite reaches now needs a unit test for that line.
- **An admin override is the only way past a red gate.** It is deliberate: the
  alternative is a label that turns the gate off for free.

### Neutral
- The whole-codebase floors in
  [ADR-003](ADR-003-coverage-counters-thresholds-and-ratchet.md) are unchanged
  and still unmet. This gate stops the tail growing; it does not shorten it.

## Related ADRs
- [ADR-002: Coverage Gates Apply to Changed Code](ADR-002-coverage-gates-apply-to-changed-code.md) — the scoping argument this implements
- [ADR-003: Coverage Counters, Thresholds and the Ratchet](ADR-003-coverage-counters-thresholds-and-ratchet.md) — the floors this does not replace
- [ADR-001: The Test Pyramid and Layer Placement](ADR-001-test-pyramid-and-layer-placement.md) — why only the base of the pyramid is measured
