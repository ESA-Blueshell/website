# ADR-003: Coverage Counters, Thresholds and the Ratchet

## Status
Accepted

## Context

[ADR-002](ADR-002-coverage-gates-apply-to-changed-code.md) settles what the gates
bind to. This one settles what they count, what they exempt, and where the numbers
go over time.

The requirement driving it is that every public method be exercised. JaCoCo cannot
express that directly: its
[report DTD](https://github.com/jacoco/jacoco/blob/master/org.jacoco.report/src/org/jacoco/report/xml/report.dtd)
gives a `<method>` only `name`, `desc` and an optional `line`. **No visibility
information is recorded anywhere in the exec or XML format**, so no rule can
select public methods. Enforcing the requirement literally would mean reading
`ACC_PUBLIC` out of the class files with ASM and owning that tooling against every
change in Kotlin codegen.

## Decision

### Counters

**METHOD = 100% on the merged unit + integration execution data.**
**BRANCH = 80% on each source set independently.**

The METHOD counter is used in place of a public-method rule. It is *stricter*,
because it includes private methods, and that is coherent rather than accidental:
a private method is reachable only from inside its own class, so an uncovered
private method proves some public path through that class was never taken. A rule
that catches a superset of the intended defect is acceptable where the extra
catches are all real.

Method completeness is a whole-system property — every public method must be
exercised by *something* — so it binds the merged exec. Branch depth is a
per-layer property, so each source set carries its own number. This split has
**no published support**; the literature treats function coverage as a weaker
criterion in the same hierarchy as branch coverage, not as a different kind of
signal. It is this repository's own reasoning and is recorded as such.

The merged binding also resolves what would otherwise be the rule's largest cost.
Property accessors are **2758 of the API's 5430 methods** — just over half. Under
a unit-only METHOD gate, every `val` on every response DTO would need a test that
reads it. Under the merged gate, an integration test that serialises the response
executes every getter through Jackson, so the accessors are covered as a
by-product of testing the endpoint.

### Exclusions

Four, and no more:

```
**/generated/**              already excluded
:libs:openapi-specs clients  generated OpenAPI clients
ApiApplication, ApiApplicationKt
db/migration                 Flyway
```

DTOs, entities, `@Configuration` classes and exceptions all **count**. The
conventional exclusion list — `**/dto/**`, `**/*Config.class`, `**/*Exception.class`
— is deliberately rejected: the `mapping` and `validation` packages sit alongside
the DTOs and hold real logic, entities in this codebase carry soft-delete sentinels
and association helpers, and a DTO with a computed property is exactly the case an
exclusion would hide.

The usual objection to this — that Kotlin data classes flood the denominator with
`copy`, `componentN`, `equals`, `hashCode` and `toString` — **does not hold here,
and was verified rather than assumed.** Across all 976 classes in the report:

| Generated member | Occurrences |
|------------------|-------------|
| `copy`, `copy$default` | 0 |
| `component1`…`componentN` | 0 |
| `toString` | 0 |
| `equals` / `hashCode` | 12 each |

JaCoCo 0.8.14 filters Kotlin data-class synthetics completely. The 12 remaining
`equals`/`hashCode` pairs are hand-written identity implementations on JPA base
classes and association entities — `AutoIdEntity`, `BoardMember`, `Contribution`,
`CommitteeMember` — which carry real semantics and deserve tests.

### The ratchet

| From | Branch floor |
|------|--------------|
| Adoption | 80% |
| 2026-12-01 | 90% |
| 2027-03-01 | 100% |

Absolute dates, because "one quarter later" rots the moment a merge slips. Each
step is a one-line change in `testing-conventions.gradle.kts`. Slowing the
schedule requires amending this ADR, which forces the argument into the open
rather than letting a floor quietly stop moving.

The 100% destination is a bet that the two published reasons it would be
unreachable do not apply to this codebase. Both were checked:

- **Coroutine state machines.** JaCoCo does not filter the `label` switch a
  `suspend` function compiles to ([jacoco#1868](https://github.com/jacoco/jacoco/issues/1868)),
  which makes 100% branch unreachable in coroutine-heavy code. This API contains
  **zero** `suspend` functions and does not depend on `kotlinx.coroutines` across
  667 files.
- **Parameter null-checks.** `Intrinsics.checkNotNullParameter` on non-nullable
  public parameters is reported as adding unfilterable branches. It does not here:
  **702 of 976 classes (71.9%) report zero branches**, including command classes
  with non-nullable constructor parameters such as `UpdateBoardCommand` (9 methods,
  0 branches). Were these checks unfiltered, every such class would carry branches.

### What the current configuration actually enforces

For the record, since it is easy to misread: the existing `minimum = "0.40"` in
`testing-conventions.gradle.kts` has no `counter`, and a bare limit defaults to
[`INSTRUCTION` / `COVEREDRATIO`](https://docs.gradle.org/current/javadoc/org/gradle/testing/jacoco/tasks/rules/JacocoLimit.html).
It is a 40% bytecode-instruction rule, not lines and not branches — and it has
never run.

## Implementation status

Decided, not yet enforced.

- Merged execution data does not exist. `JacocoMerge` is
  [deprecated](https://github.com/gradle/gradle/issues/12767); the merged report and
  verification tasks take multiple `executionData` entries instead.
- The 80% branch floors replace the `0.40` instruction rules, and verification must
  be invoked from `validate.yml`.
- **Kotlin is on 2.4.10 and JaCoCo on 0.8.14.** Compatibility bridges generated for
  functions declared in interfaces are not filtered until
  [0.8.15](https://github.com/jacoco/jacoco/issues/1905). Upgrading JaCoCo is a
  prerequisite for trusting the METHOD counter on interface-heavy packages, and
  should land before the gate goes blocking.
- The unit branch floor must not go blocking until the 27 misplaced tests move
  ([ADR-001](ADR-001-test-pyramid-and-layer-placement.md)).

## Consequences

### Positive
- **The rule is enforceable with stock tooling.** No ASM, no bytecode parsing, no
  custom annotation.
- **The exclusion list is short enough to hold in mind**, and every entry is
  generated code or a framework entry point rather than a judgement about what
  deserves testing.
- **The 100% destination is evidence-backed for this codebase**, not aspiration:
  the two mechanisms that would make it unreachable were checked and are absent.

### Negative
- **METHOD = 100% is harsher than the stated intent.** Private methods are included,
  and a class with an unreachable private helper fails until the helper is deleted
  or reached. Deleting it is usually the right answer, but the gate does not say so.
- **Nested elvis chains still miscount.** JaCoCo filters safe-call and elvis
  bytecode, but [jacoco#921](https://github.com/jacoco/jacoco/issues/921) remains
  open for nested cases, so a small number of branches will read as missed while
  the code is exercised.
- **A dated ratchet will collide with a release at some point.** The 2027-03-01 step
  raises the floor 10 points on a date chosen a long time in advance.
- **100% is a mandate, and mandates get gamed.** Marick's
  [How to Misuse Code Coverage](http://www.exampler.com/testing-com/writings/coverage.pdf)
  names mandated percentages as the primary misuse; Fowler treats high numbers as
  [too easily reached by low-quality testing](https://martinfowler.com/bliki/TestCoverage.html).
  Nothing in this ADR prevents a test that calls a method and asserts nothing.

### Neutral
- **Assertions, not coverage, predict effectiveness**
  ([Zhang and Mesbah, FSE 2015](https://people.ece.ubc.ca/amesbah/resources/papers/fse15.pdf)).
  Mutation testing measures what a coverage gate cannot, and PIT is viable on a
  Spring Boot codebase. It is not adopted here — the cost is a separate decision —
  but it is the honest answer to "does the suite actually catch anything".
- **Half the METHOD denominator is property accessors.** Covered incidentally
  through serialisation rather than by dedicated tests, which is the intended
  outcome, not a loophole.

## Related ADRs
- [ADR-002: Coverage Gates Apply to Changed Code](ADR-002-coverage-gates-apply-to-changed-code.md) — the scoping these numbers apply within
- [ADR-004: The Public Surface Is the Unit of Test](ADR-004-public-surface-is-the-unit-of-test.md) — why METHOD stands in for a public-method rule
- [ADR-005: Frontend Coverage Parity](ADR-005-frontend-coverage-parity.md) — the same numbers and dates on the frontend
