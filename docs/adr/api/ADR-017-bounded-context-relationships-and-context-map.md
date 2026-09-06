# ADR-017: Bounded Context Relationships

## Status
Accepted. Narrowed on 2026-09-06 to what the build does not check (#907).

## Context

This ADR used to carry a hand-drawn context map: every bounded context, the arrows between
them, the events each published and the entities they shared. Nothing checked any of it, so it
was the only record there was.

It is no longer the only record, and it had drifted badly. It opened by naming nine packages
when there are twenty modules; it named `membership`, which is not a package at all — a
`Membership` is a `user` entity; it placed `email`, `contact` and `jobs` under a shared-service
heading when each is a top-level module; and it omitted `board`, `cohort`, `esports`, `file`,
`oidc`, `security`, `sync` and `telemetry` entirely. Roughly half the application was missing,
misplaced or fictional. Its own Negative consequence — *"Maintenance overhead: requires
disciplined updates"* — is what came due.

What replaced it is machine-checked. Every module declares an `@ApplicationModule` whitelist
naming the modules it may reach and the named interface it reaches them through;
`ApplicationModuleDetectionTest` runs `modules.verify()` over the compiled classes, so an
undeclared dependency, a cycle, or a reach for a type a module does not publish fails the
build. A second copy of that in prose can only drift away from it.

## Decision

**Which modules exist and what they may reach is read from the module metadata, not from here.**
`net.blueshell.api.<module>.ModuleMetadata` declares it and the build enforces it.

**This ADR keeps what the build cannot check**: what a relationship *means*, and how the
application is protected from the systems it does not own.

The build records that `esports` may reach `user`. It cannot record that the relationship is
Customer/Supplier with `user` as Open Host, that `user` is therefore not free to reshape what it
publishes on a whim, or that an external API gets an Anti-Corruption Layer because its model is
not ours to adopt. That is the knowledge worth writing down by hand, because nothing else holds
it.

### Relationship pattern definitions

**Partnership** — two modules depend on each other, and changes are coordinated directly. Used
sparingly, and worth questioning every time: a partnership that could be a Customer/Supplier
usually should be.

**Customer/Supplier** — the upstream provides, the downstream consumes and can ask for what it
needs. The most common shape here.

**Open Host** — a module many others depend on, publishing a named interface it keeps stable.
`user` is the clearest: most modules read a user, and what they may read is what its named
interface publishes.

**Conformist** — the downstream takes the upstream's model as it stands, without translation.
Reasonable when the upstream is stable and the translation would buy nothing.

**Separate Ways** — no integration at all. `blog` and `sponsor` are near enough this.

**Anti-Corruption Layer** — a translation layer at the edge of a system we do not own, so its
model cannot reach the domain. Required for every third-party API, without exception.

### Choosing between them

Prefer Customer/Supplier. Reach for Open Host when many modules depend on one and its interface
has to stay still for them. Reach for Partnership only when a mutual dependency is genuinely
unavoidable, and say why in the ADR that introduces it. Use an ACL for anything external,
always.

## Anti-Corruption Layers (external systems)

### Listmonk Transactional Email API
- **Location**: `platform/integration/email/` (`ListmonkEmailClient`)
- **Purpose**: Deliver transactional emails (registration, event signup, recovery)
- **Translation**: `EmailContent` DTO → Listmonk transactional message format
- **Protection**: The domain knows nothing of Listmonk template ids, subscriber modes or headers
- **Active profiles**: `!test` (dev + prod)

### Listmonk Subscribers/Lists API
- **Location**: `platform/integration/contact/` (`ListmonkContactAdapter`)
- **Purpose**: Sync user contacts and list memberships to Listmonk
- **Translation**: `ContactData` domain object → Listmonk subscriber/list format
- **Active profiles**: `!test` (dev + prod; primary adapter)

### Brevo Contacts API
- **Location**: `platform/integration/contact/` (`BrevoContactAdapter`)
- **Purpose**: Sync user contacts to Brevo (production fallback / coexistence)
- **Translation**: `ContactData` domain object → Brevo contact attributes format
- **Active profiles**: `!test & !dev` (production only; secondary adapter)

### Google Calendar API
- **Location**: `platform/integration/calendar/` (`GoogleCalendarAdapter`)
- **Purpose**: Publish approved events to an external calendar
- **Translation**: Event entity → Google Calendar Event format
- **Active profiles**: `!test & !dev` (production only)

> This inventory has not been checked against the code by this ADR's narrowing, and at least one
> entry is known to be wrong: a Mollie Payment API ACL was listed here with a location reading
> "if exists", and `Mollie` appears in no file under `services/api/src/main`. Discord, which
> appears in forty-four, was never listed. #1196 checks the inventory against the code and takes
> this note out; #907 left it out of the narrowing so that a rewrite did not become an audit.

## Consequences

### Positive
- One record of which modules may reach which, and the build fails when it is wrong
- What survives here is knowledge nothing else holds, so it is worth the maintenance
- A new module does not need this file edited, which is what made the old map rot

### Negative
- The patterns are still hand-maintained, and a relationship that changes shape without an ADR
  will still go unrecorded. Nothing checks intent; only the arrows are checked
- Reading "which modules exist" now means reading module metadata rather than one page

## Guidelines

### DO
- ✅ Read the module metadata for which modules exist and what they may reach
- ✅ Name the pattern when a new relationship is introduced, and say why in that ADR
- ✅ Put an ACL in front of every external system
- ✅ Question a Partnership: it is usually a Customer/Supplier that has not been separated yet

### DON'T
- ❌ Restate the module graph here. It is checked elsewhere, and a second copy drifts
- ❌ Integrate with an external API without a translation layer
- ❌ Allow a Shared Kernel without explicit governance (see ADR-020)

## References

- Eric Evans, *Domain-Driven Design Reference* (Context Mapping patterns) ([Domain Language][1])
- Vernon, *Implementing Domain-Driven Design* (Strategic design chapters)
- [architecture/ADR-001](../architecture/ADR-001-application-modules-replace-layers.md) — what the build checks
- [architecture/ADR-003](../architecture/ADR-003-package-topology-and-placement-rules.md) — where a class goes

[1]: https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf "Domain-Driven Design Reference"
