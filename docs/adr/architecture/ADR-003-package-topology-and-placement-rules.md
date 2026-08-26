# ADR-003: Package Topology and Placement Rules

## Status
Accepted

## Context

Finding code costs more than it should, and adding it means guessing where it
goes. [ADR-022](../api/ADR-022-platform-infrastructure-shared-organization.md)
was written to fix precisely this, with decision trees for `shared`,
`infrastructure` and `platform`. Its own Context section lists the questions it
set out to answer — *"Where do job definitions go?"*, *"Is permission evaluation
web or infrastructure?"* — and the questions are still being asked. Documenting
a taxonomy did not make the taxonomy predictive.

What the code shows:

**The top-level split does not track anything.** `platform/integration/email` has
`web/dto`, `persistence/repository`, `persistence/spec` and `application/service`
— structurally identical to `domain/committee`. Email is as much a feature as
blog is; it sits under `platform` for historical reasons.

**One feature, two packages, repeatedly.**

| Feature | Split across |
|---------|--------------|
| Job execution | `platform/integration/queue` (engine) and `platform/integration/job` (entity, repository, controller) |
| OIDC | `platform/oidc` (6 config classes) and `platform/web/oidc` (2 controllers) |
| Current user | `shared/security` (the `CurrentUserProvider` interface) and `infrastructure/security` (its implementation) |
| Dirty tracking | `shared/hibernate` (`DirtyField`, `DirtyModel`) and `shared/model/hibernate` (`DirtyTrackingInterceptor`) |
| Controllers | `platform/web`, `shared/web` and every `domain/*/web` |

**`shared` is a bag, and fan-in proves which parts.** Counting how many other
modules import each package:

| Package | Files | Consumer modules |
|---------|-------|------------------|
| `shared/enums` | 12 | 22 |
| `shared/model` | 13 | 19 |
| `shared/repository` | 1 | 17 |
| `shared/service` | 1 | 13 |
| `shared/job`, `shared/tracking` | 9, 3 | 10, 10 |
| `infrastructure/security/permission` | **19** | **2** |
| `shared/validation/date`, `shared/jpa` | 2, 1 | **0** |

Nineteen permission classes serve two modules. `BlogPermission` belongs with
blog. Two packages have no cross-module consumer at all.

**Depth.** 171 files sit five packages below the base and two sit six deep.
`net.blueshell.api.domain.committee.web.dto.response.CommitteeResponse` is seven
segments, of which `domain` and `dto` carry no information.

## Decision

**Modules are flat under the base package. Inside a module there are four
folders and nothing deeper. Placement follows six rules, and most of them are
measurable.**

### Topology

```
net.blueshell.api
├─ ApiApplication, global @Configuration,          ← the application, not a module
│  exception advice, CsrfController, MainController
│
├─ user/ 87   auth/ 42   event/ 66                 ← feature modules
│  contribution/ 40   survey/ 39   committee/ 23
│  board/ 20   file/ 18   telemetry/ 15
│  sponsor/ 13   blog/ 13
│
├─ cohort/ 59   contact/ 20   email/ 18            ← capability modules
│  sync/ 18 (calendar folded in)
│  jobs/ ~16 (queue + job merged)
│  oidc/ 8 (both halves merged)
│
├─ security/ ~14   base and composite evaluator only
└─ shared/  ~30    @ApplicationModule(type = OPEN)
```

Modulith detects modules as direct sub-packages of the base package, so a
grouping level would make `domain` and `platform` the modules. Flattening is a
requirement of [ADR-001](ADR-001-application-modules-replace-layers.md), not a
preference — and it removes the two segments that carried no meaning.

Types directly in `net.blueshell.api` belong to no module; that is where global
wiring lives. Most of `platform/config`'s 22 classes are not global and move to
the module they configure — `BrevoClientConfig` to `contact`, `OidcJwtConfig` to
`oidc`.

### Inside a module

```
<module>/
├─ api/           what other modules may call — published views, events, ports
├─ web/           controllers, input types, responses, mappers
├─ domain/        entities, use-case services, this module's Permission
└─ persistence/   repositories, specifications
```

`net.blueshell.api.user.web.UserController` — five segments, down from seven.
`web/dto/request`, `web/dto/response`, `web/mapping/request`,
`web/mapping/response` and `application/command` all disappear; most of them
were emptied by [ADR-002](ADR-002-use-case-services-replace-the-command-bus.md)
before they were flattened.

**`api` means the module's surface, not REST.** A controller is internal —
nothing outside `user` calls `UserController`. The collision with the base
package is real and accepted: `net.blueshell.api.user.api.UserView` reads oddly,
but the prefix is invisible from inside the module, and renaming the base
package across 660 files buys nothing but appearance.

### The six placement rules

1. **Another module uses it → `<module>/api`.** Otherwise internal. Enforced by
   the named-interface convention, not by convention alone.
2. **Only one module uses it → it does not belong in `shared`.** Fan-in is
   countable, so this rule can be checked rather than argued.
3. **One feature, one module.** This single rule closes every split in the table
   above.
4. **Authorization lives beside what it authorizes.** `BlogPermission` in
   `blog`, `EventPermission` in `event`. The abstract base and the composite
   evaluator stay in `security`, which is what genuinely serves everyone.
5. **A fake adapter lives beside the port it replaces.** The seven
   `@Profile("test | dev")` doubles in `platform/integration/mock` are real
   adapters, correctly in main sources so the dev profile can reach them — but
   `InMemoryEmailClient` belongs in `email`, next to the client it stands in for.
6. **Global wiring with no owning module → the application root.**

Rules 4 and 5 are the same rule as 3, applied to things that were centralised by
role rather than by feature. That is the pattern behind most of the searching:
code was filed by what kind of thing it is, when people look for it by what it
is about.

## Implementation status

Decided, none of it moved. Phases 1 and 3 of
[ADR-006](ADR-006-migration-sequencing.md).

- `shared` narrows to the seven packages with fan-in above ten;
  `shared/validation/date` and `shared/jpa` have no cross-module consumer and
  are absorbed or deleted.
- The 19 permission classes distribute to their modules. Controllers reference
  them through Spring's `hasPermission(...)` SpEL rather than by import, which
  is why their measured fan-in reads as 2 and why moving them is invisible to
  the compiler — the permission tests are what must catch a mistake.
- Roughly 230 test files mirror these packages and move with them.
- No check enforces rule 2. A test asserting that every `shared` package has more
  than one consumer module would close it, and would have caught the two
  zero-consumer packages years ago.

## Consequences

### Positive
- **Two noise segments leave every import**, and the deepest package drops from
  seven segments to five.
- **Placement is answerable from the file itself.** Rules 1 and 2 are counting
  exercises; rule 3 is a search.
- **`shared` cannot silently regrow**, because fan-in makes "is this actually
  shared?" a question with a number for an answer.
- **Adding a domain stops editing central packages** — no permission registry to
  extend, no `platform` bucket to choose.

### Negative
- **Twenty top-level packages** is a long flat list where there used to be four
  short ones. It is greppable rather than navigable, which suits search but not
  browsing.
- **`api` will be misread as REST**, repeatedly, by anyone who has not read this
  ADR. A `@PackageInfo` doc comment on each is the only mitigation.
- **Nothing enforces the internal four folders.** Modulith checks the module
  boundary and the `api` surface; `web` versus `domain` is convention.
- **A very large diff.** Roughly 660 main files and 230 test files change
  package, and every `git blame` depends on those commits containing renames
  only.

### Neutral
- **Module size stays uneven** — user at 87 files, calendar folded away at 3.
  Evening it out would mean splitting by size rather than by meaning, which is
  how the current structure was arrived at.

## Related ADRs
- [ADR-001: Application Modules Replace Layers](ADR-001-application-modules-replace-layers.md) — why flat is required rather than preferred
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — what empties the deep web packages
- [ADR-006: Migration Sequencing](ADR-006-migration-sequencing.md) — when this moves
- [API ADR-022: Platform, Infrastructure, and Shared Organization](../api/ADR-022-platform-infrastructure-shared-organization.md) — superseded by this record
- [API ADR-020: Shared Kernel Governance](../api/ADR-020-shared-kernel-governance.md) — superseded; fan-in replaces its rules
