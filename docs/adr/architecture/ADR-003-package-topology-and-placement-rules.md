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
│  exception advice, CsrfController, MainController      38 files
│
├─ user/ 93   event/ 68   auth/ 39                 ← feature modules
│  contribution/ 39   survey/ 35   esports/ 21
│  committee/ 23   board/ 21   file/ 16
│  blog/ 13   telemetry/ 13   sponsor/ 12
│
├─ cohort/ 55   jobs/ 26 (queue + job merged)      ← capability modules
│  email/ 22   contact/ 20
│  sync/ 18 (calendar folded in)
│  oidc/ 6 (both halves merged)
│
├─ security/ 17   @ApplicationModule(type = OPEN)
│                 base and composite evaluator,
│                 CurrentUserProvider
└─ shared/  51    @ApplicationModule(type = OPEN)
```

Twenty modules, 653 main source files. `esports` is a feature module; an
earlier version of this table omitted it. The seven `@Profile("test | dev")`
doubles now under `platform/integration/mock` are not a module — rule 5
distributes them to the ports they stand in for.

Both `shared` and `security` are open, which exempts them from cycle detection.
[ADR-001](ADR-001-application-modules-replace-layers.md) records why, and what
it costs.

Flattening removes the two segments that carried no meaning. It is a
**preference, not a requirement**: an earlier version of this record said
Modulith detects modules only as direct sub-packages of the base package, so a
grouping level would force `domain` and `platform` to be the modules. That is
wrong — `getModuleBasePackages` returns arbitrary packages, so a detection
strategy nominates the nested ones and verification runs against the current
layout. Flattening is therefore sequenced **after** verification in
[ADR-006](ADR-006-migration-sequencing.md), so the boundaries are already
enforced when 653 files move.

Types directly in `net.blueshell.api` belong to no module; that is where global
wiring lives. Most of `platform/config`'s 22 classes are not global and move to
the module they configure — `BrevoClientConfig` to `contact`, `OidcJwtConfig` to
`oidc`.

### Inside a module

```
<module>/
├─ api/           what other modules may call — published views, events, ports
│                 @NamedInterface("api")
├─ web/           controllers, input types, responses, mappers
├─ domain/        entities, use-case services, this module's Permission
└─ persistence/   repositories, specifications
                  @NamedInterface("entities")
```

**Two named interfaces, not one.** `api` publishes services, views, events and
ports, and any module may reach it. `persistence` is published separately as
`entities`, and only a module that names it may reach it:

```kotlin
@PackageInfo
@ApplicationModule(allowedDependencies = ["user :: api", "user :: entities", ...])
class ModuleMetadata
```

That exists because [API ADR-013](../api/ADR-013-entity-association-pattern.md)
permits an owning-side JPA reference to cross a module boundary — `File.uploader`
and `CommitteeMember.user` both hold a real foreign key into `user`. Those
references have to compile, so `User` has to be reachable; publishing it through
`api` would make every module's entities permanently public and let verification
bless the reaching-into-persistence this ADR set exists to stop. Naming the
dependency pair by pair keeps the list of who touches whose tables short,
explicit and reviewable.

The cost is that `allowedDependencies` is a whitelist. A module that declares it
to reach `user :: entities` must enumerate **all** its allowed dependencies, not
just that one. Only the two or three modules holding a cross-module owning
reference pay it.

`net.blueshell.api.user.web.UserController` — five segments, down from seven.
`web/dto/request`, `web/dto/response`, `web/mapping/request`,
`web/mapping/response` and `application/command` all disappear; most of them
were emptied by [ADR-002](ADR-002-use-case-services-replace-the-command-bus.md)
before they were flattened.

**`api` means the module's surface, not REST.** A controller is internal —
nothing outside `user` calls `UserController`. The collision with the base
package is real and accepted: `net.blueshell.api.user.api.UserView` reads oddly,
but the prefix is invisible from inside the module, and renaming the base
package across 653 files buys nothing but appearance.

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

Partly done. The placement rules have been applied; the flattening has not, and
is now the **last** phase of [ADR-006](ADR-006-migration-sequencing.md) rather
than the third.

Already applied:

- `shared` has narrowed to the packages with real fan-in; single-consumer code
  moved to its owning module, and `shared/validation/date` and `shared/jpa` are
  gone.
- The permission classes have distributed to their modules under
  [ADR-007](ADR-007-authorization-lives-with-its-aggregate.md), leaving the base
  and the composite in `security`.

Outstanding:

- **The flattening itself**: 653 main files and 228 api test files change
  package, in rename-only commits, one module per commit.
- **The two named interfaces** and the `allowedDependencies` whitelists.
- **30 imports reach another module's `web` package**, naming 16 distinct
  controller DTOs. Those are a defect rather than a surface: they are inverted or
  copied, not published.
- No check enforces rule 2. A test asserting that every `shared` package has more
  than one consumer module would close it, and would have caught the two
  zero-consumer packages years ago.
- Rename-only discipline now has an enforcement mechanism it lacked: the diff
  task behind the coverage gates resolves renames with `git diff -M`, so a pure
  rename reports `R100` and is exempt while a rename mixed with an edit reports
  `R0xx` and is gated.

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
- **A very large diff.** 653 main files and 228 api test files change
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
