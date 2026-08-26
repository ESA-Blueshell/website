# ADR-002: Coverage Gates Apply to Changed Code

## Status
Accepted

## Context

No coverage gate runs in CI. `jacocoTestCoverageVerification` is configured in
`build-logic/src/main/kotlin/testing-conventions.gradle.kts` with a floor of
`0.40`, and `validate.yml` never invokes it — the pipeline runs `test`,
`integrationTest` and the two report tasks, and `check` is not called. JaCoCo does
not wire verification into `check` by default, so the floor has never failed a
build.

That floor is also not what it appears to be. A `limit` block with a bare
`minimum` and no `counter` defaults to
[`INSTRUCTION` / `COVEREDRATIO`](https://docs.gradle.org/current/javadoc/org/gradle/testing/jacoco/tasks/rules/JacocoLimit.html),
so the rule reads "40% of bytecode instructions", not lines and not branches.

Turning a gate on therefore means choosing what it binds to. The last valid
full measurement — 19 August, unit exec only — put the API at 52% branch
(1837/3532) and 67.7% instruction. Any whole-codebase gate at the intended numbers
fails on its first run, which leaves three ways forward: lower the numbers until
they pass, exempt the existing shortfall, or bind the gate to new work.

## Decision

**Gates bind the code a change touches. The legacy tail is never retro-fitted
wholesale; it improves as it is worked on.**

This is SonarQube's
[Clean as You Code](https://docs.sonarsource.com/sonarqube-server/10.5/user-guide/clean-as-you-code/)
argument: the coverage of a module nobody is editing is not actionable, whereas the
coverage of the lines in front of a reviewer is. It is the only variant that can be
switched to blocking without either weakening the target or publishing a list of
exemptions that outlives everyone who understands it.

### Two granularities, because the counters differ in kind

**Branch coverage gates on changed lines.** A branch is a property of a line, so
intersecting it with a diff is well-defined. Off-the-shelf tooling does this:
[delta-coverage](https://github.com/gw-kit/delta-coverage-plugin) and
[diff-coverage-gradle](https://github.com/form-com/diff-coverage-gradle) are both
Gradle-native, both consume JaCoCo XML, and both support LINE and BRANCH with a
fail flag.

**Method coverage gates on changed files.** No diff-coverage tool enforces a METHOD
rule, and the reason is structural rather than an oversight: the JaCoCo XML
[report DTD](https://github.com/jacoco/jacoco/blob/master/org.jacoco.report/src/org/jacoco/report/xml/report.dtd)
gives a `<method>` only `name`, `desc` and an optional `line` — its *first* line.
A method whose body changed but whose signature line lies outside the diff cannot
be identified.

Stock JaCoCo covers the case directly, at file granularity:

```kotlin
violationRules {
    rule {
        element = "CLASS"
        includes = changedClassPatterns   // computed from git diff against the merge base
        limit {
            counter = "METHOD"
            value = "COVEREDRATIO"
            minimum = "1.0".toBigDecimal()
        }
    }
}
```

File granularity is not a compromise here — it is the correct granularity. Method
coverage asks whether a class's surface is exercised, which is a property of the
whole class. Touch a class and own all of its methods; that is the rule, and it is
the same rule a reviewer would apply unaided.

### The baseline is recorded, not enforced

The 19 August figures above are the reference point for the ratchet in
[ADR-003](ADR-003-coverage-counters-thresholds-and-ratchet.md). They are not a
floor and no PR is measured against them.

A re-measurement attempted for this ADR is deliberately **not** cited: it ran
without a database, 455 of its 461 failures were
`ApplicationContext failure threshold (1) exceeded`, and coverage taken from a run
where a third of the suite never executed is an artifact. The cause is the source-set
contamination recorded in [ADR-001](ADR-001-test-pyramid-and-layer-placement.md),
and a trustworthy unit baseline exists only after those 27 files move.

## Implementation status

Decided, not yet enforced. Three pieces are missing:

- A diff-coverage plugin wired into `testing-conventions.gradle.kts` for the branch
  gates, resolving the diff against the PR's merge base rather than `HEAD~1`.
- A task computing `changedClassPatterns` from `git diff --name-only` for the method
  gate.
- A `validate.yml` step that actually invokes verification. Without it this ADR
  changes nothing, exactly as the existing `0.40` floor changes nothing.

Until these land, gates run in report-only mode.

## Consequences

### Positive
- **The gate can go blocking without a debt list.** No exemption file, no
  suppression annotations, nothing to prune later.
- **Accountability sits with the change.** A reviewer sees a number about the diff
  in front of them rather than a project-wide average that moves too slowly to
  inform anything.
- **Legacy code is not punished for being touched lightly** — at file granularity
  for methods and line granularity for branches, a one-line fix does not drag in a
  300-line class's branch debt.

### Negative
- **Moved code reads as new.** Extracting a class shows every line as added, so a
  pure refactor of untested code fails the gate. This is SonarQube's best-documented
  failure mode and it will be hit here.
- **The gate can discourage touching risky code**, which is the opposite of the
  intent: the least-tested code becomes the most expensive to improve.
- **Method-at-file-granularity is harsher than branch-at-line-granularity.** Adding
  one method to a class with nine untested ones fails until all ten are covered.
- **Two mechanisms mean two ways to be wrong.** A plugin for branches and a
  hand-computed include list for methods is more moving parts than one gate.

### Neutral
- **Coverage is a weak proxy for suite quality.** Inozemtseva and Holmes
  ([ICSE 2014](https://www.cs.ubc.ca/~rtholmes/papers/icse_2014_inozemtseva.pdf))
  found the correlation between coverage and effectiveness drops to low-to-moderate
  once suite size is controlled for; Zhang and Mesbah
  ([FSE 2015](https://people.ece.ubc.ca/amesbah/resources/papers/fse15.pdf)) found
  assertion count is the stronger predictor. A gate cannot see assertions. What it
  can do is stop untested code arriving, which is a narrower claim than "the tests
  are good" and is the only claim made here.

## Related ADRs
- [ADR-001: The Test Pyramid and Layer Placement](ADR-001-test-pyramid-and-layer-placement.md) — why the unit baseline is not yet trustworthy
- [ADR-003: Coverage Counters, Thresholds and the Ratchet](ADR-003-coverage-counters-thresholds-and-ratchet.md) — the numbers these gates carry
- [ADR-005: Frontend Coverage Parity](ADR-005-frontend-coverage-parity.md) — the same scoping on the frontend
