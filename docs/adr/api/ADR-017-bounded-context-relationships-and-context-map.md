# ADR-017: Bounded Context Relationships and Context Map

## Status
Accepted

## Context

The application consists of multiple bounded contexts (domains) organized as packages: `auth`, `user`, `event`, `committee`, `membership`, `contribution`, `blog`, `sponsor`, and `survey`. While each context has clear internal structure (per ADR-001), the **relationships between contexts** are implicit.

Eric Evans' strategic DDD emphasizes the **Context Map**: a "large-scale view" of model development across the project that prevents boundaries from bleeding into each other and clarifies relationships. ([Domain Language][1])

Evans also provides relationship patterns including:
- **Shared Kernel** - jointly owned model subset with tight coordination
- **Customer/Supplier** - downstream has some influence on upstream
- **Conformist** - downstream accepts upstream model as-is
- **Anti-Corruption Layer** - isolating translation layer
- **Partnership** - two contexts with mutual dependency
- **Separate Ways** - no relationship; complete autonomy

Without explicit documentation:
- Integration choices are tribal knowledge
- Boundary drift can occur unnoticed
- Refactoring impacts are unclear
- New developers struggle to understand relationships

## Decision

We maintain an **explicit Context Map** documenting:

1. **All bounded contexts** in the system
2. **Relationships between contexts** using DDD relationship patterns
3. **Integration points** (APIs, events, shared data)
4. **Ownership** (team or primary maintainer)

### Current Context Map

```
┌──────────────────────────────────────────────────────────────┐
│                      Blueshell API Context Map                │
└──────────────────────────────────────────────────────────────┘

[Auth] ──Partnership──> [User]
  │
  └── publishes: UserCreated, PasswordChanged
  └── depends on: UserService (for authentication)

[User] ──Open Host──> [*]
  │
  ├── provides: User entity (shared reference)
  ├── provides: UserService (user queries)
  └── publishes: UserCreated, UserUpdated, UserActivated

[Membership] ──Customer/Supplier──> [User]
  │
  ├── subscribes: UserCreated
  └── depends on: User entity (FK reference)

[Event] ──Customer/Supplier──> [User]
  │         ──Customer/Supplier──> [Committee]
  │         ──Partnership──> [Survey]
  │
  ├── depends on: User entity (organizers, sign-ups)
  ├── depends on: Committee entity (FK reference)
  ├── integrates: Survey (embedded survey data)
  └── publishes: EventCreated, EventPublished, SignUpCreated

[Committee] ──Open Host──> [Event]
  │
  ├── provides: Committee entity (shared reference)
  └── depends on: User entity (members)

[Contribution] ──Customer/Supplier──> [User]
  │
  ├── depends on: User entity (contributor reference)
  └── depends on: ContributionPeriod (local aggregate)

[Blog] ──Separate Ways──> [*]
  │
  └── independent content management (minimal integration)

[Sponsor] ──Separate Ways──> [*]
  │
  └── independent sponsor management (minimal integration)

[Survey] ──Conformist──> [Event]
  │
  └── embedded within Event context (owned by Event)

──────────────────────────────────────────────────────────────
Platform (Shared Service Bounded Context):

[Platform: Email] ──Open Host Service──> [Auth, Event, Contribution]
  │
  ├── provides: EmailService (send transactional emails)
  ├── consumes: EmailContent DTO from domains (via shared/)
  ├── owns: emails table (outbox with delivery tracking)
  └── integrates: Listmonk transactional API (ACL)

[Platform: Contact] ──Open Host Service──> [User]
  │
  ├── provides: ContactSyncService, ContactListService
  ├── subscribes: UserCreated, UserUpdated (via ContactSyncScheduler)
  ├── owns: contacts, contact_lists, contact_list_memberships tables
  └── integrates: Listmonk subscribers API (primary) + Brevo (production fallback)
      Note: ListmonkContactAdapter active in dev+prod; BrevoContactAdapter production-only

[Platform: Job] ──Infrastructure Service──> [*]
  │
  ├── provides: JobDispatcher (enqueue), JobExecutionService (query/retry)
  ├── owns: job_executions table
  └── executes: @Async thread pool + RetryTemplate (see ADR-023)

──────────────────────────────────────────────────────────────
External Systems (Anti-Corruption Layers via platform/integration/):

[Platform: Email] ──ACL──> [Listmonk transactional API]
[Platform: Contact] ──ACL──> [Listmonk subscribers/lists API] (primary)
[Platform: Contact] ──ACL──> [Brevo Contacts API] (production fallback)
[Platform: Calendar] ──ACL──> [Google Calendar API]
[Contribution] ──ACL──> [Mollie Payment API]
```

### Relationship Pattern Definitions

**Partnership** (Mutual Dependency)
- Two contexts depend on each other
- Changes coordinated through direct communication
- Used sparingly (only Auth ↔ User currently)
- Example: Auth needs User for authentication; User needs Auth tokens

**Customer/Supplier** (Downstream requests upstream features)
- Upstream provides services to downstream
- Downstream can influence upstream priorities
- Most common pattern in our system
- Example: Membership depends on User entity

**Open Host** (Published Service)
- Context provides well-documented API/events for many consumers
- Multiple contexts depend on it
- Examples: User (referenced by most domains), Committee (referenced by Event)

**Conformist** (Accept upstream model)
- Downstream accepts upstream's model without translation
- Used when upstream is stable and well-designed
- Example: Survey conforms to Event's survey structure

**Separate Ways** (No integration)
- Contexts operate independently
- No shared data or integration
- Examples: Blog, Sponsor (mostly independent)

**Anti-Corruption Layer** (ACL)
- Translation layer for external systems
- Protects domain model from external influence
- Required for all third-party APIs
- Examples: Google Calendar, Brevo, Mollie integrations

## Consequences

### Positive
- **Visibility**: Integration choices are explicit and reviewable
- **Governance**: Changes to relationships require ADR update
- **Onboarding**: New developers understand domain relationships
- **Refactoring**: Impact analysis is clearer
- **Architecture review**: Regular review prevents boundary erosion
- **Communication**: Teams know who to coordinate with

### Negative
- **Maintenance overhead**: Requires disciplined updates
- **Ceremony**: Adds explicit governance step
- **Initial effort**: Documenting existing relationships takes time

### Trade-offs
- **Formality vs Agility**: More structure for better coordination
- **Documentation burden vs Clarity**: Upfront work for long-term benefit

## Guidelines

### DO:
- ✅ Update Context Map when adding new domains
- ✅ Document relationship changes in ADR updates
- ✅ Review Context Map during architectural discussions
- ✅ Use DDD relationship patterns consistently
- ✅ Identify integration events and shared entities
- ✅ Question Partnership relationships (usually avoidable)
- ✅ Add ACLs for all external system integrations

### DON'T:
- ❌ Let relationships form without documentation
- ❌ Skip ADR update for relationship changes
- ❌ Use Partnership pattern without strong justification
- ❌ Allow Shared Kernel without explicit governance (see ADR-020)
- ❌ Integrate directly with external APIs without ACL
- ❌ Create circular dependencies between contexts

### When to Update the Context Map

**Required:**
- Adding a new bounded context
- Changing integration patterns between contexts
- Adding/removing event subscriptions
- Introducing shared entities
- Refactoring cross-domain dependencies

**Recommended:**
- Quarterly architecture reviews
- Before major refactoring initiatives
- During onboarding sessions for new team members

### Relationship Selection Criteria

**Choose Partnership** when:
- Mutual dependency is unavoidable
- Both contexts evolve together
- Close coordination is acceptable
- Example: Auth and User are tightly coupled by nature

**Choose Customer/Supplier** when:
- Clear provider/consumer relationship
- Downstream can request features
- Loose coupling is possible
- Most common pattern

**Choose Open Host** when:
- Many consumers depend on the context
- Well-documented API/events needed
- Context is stable and foundational
- Examples: User, Committee

**Choose Anti-Corruption Layer** when:
- Integrating external systems
- Upstream model is poor fit
- External API is unstable/unreliable
- Examples: All third-party APIs

## Integration Points Inventory

### Events Published (Domain Events)

| Context | Events | Subscribers |
|---------|--------|-------------|
| User | UserCreated, UserUpdated, UserActivated | Auth, Membership |
| Event | EventCreated, EventPublished, SignUpCreated | Committee |
| Auth | PasswordChanged, TokenIssued | User |
| Membership | MembershipCreated, MembershipChanged | User |

### Shared Entities (FK References)

| Entity | Owner | Referenced By |
|--------|-------|---------------|
| User | User domain | Auth, Membership, Event, Committee, Contribution |
| Committee | Committee domain | Event |
| Event | Event domain | - |
| ContributionPeriod | Contribution domain | Contribution |

### Application Service Dependencies

| Consumer | Provider Service | Purpose |
|----------|------------------|---------|
| Auth | UserService | Authentication lookup |
| Membership | UserService | User validation |
| Event | UserService, CommitteeService | Organizer/member lookup |
| Contribution | UserService | Contributor lookup |
| Auth (via event listener) | Platform: EmailService | Send recovery/activation emails |
| Event (via event listener) | Platform: EmailService | Send event signup confirmation emails |
| Contribution (via event listener) | Platform: EmailService | Send contribution reminder emails |
| User (via ContactSyncScheduler) | Platform: ContactSyncService | Sync user profile changes to Listmonk |

## Anti-Corruption Layers (External Systems)

### Listmonk Transactional Email API
- **Location**: `platform/integration/email/` (`ListmonkEmailClient`)
- **Purpose**: Deliver transactional emails (registration, event signup, etc.)
- **Translation**: `EmailContent` DTO → Listmonk transactional message format
- **Protection**: Domain unaware of Listmonk template IDs, subscriber modes, headers
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
- **Purpose**: Publish approved events to external calendar
- **Translation**: Event entity → Google Calendar Event format
- **Protection**: Domain unaware of Google's API structure
- **Active profiles**: `!test & !dev` (production only)

### Mollie Payment API
- **Location**: `platform/integration/payment/` (if exists)
- **Purpose**: Contribution payment processing
- **Translation**: Payment requests → Mollie API format
- **Protection**: Domain unaware of Mollie's payment flow

## Review and Governance

### Context Map Reviews

**Monthly** (lightweight):
- Review new integrations added
- Check for undocumented relationships
- Validate event subscriptions are current

**Quarterly** (comprehensive):
- Full Context Map walkthrough
- Evaluate relationship patterns
- Identify refactoring opportunities
- Update documentation

**Ad-hoc** (as needed):
- Before major refactoring
- When adding new bounded contexts
- During architecture decisions

### Evolution Triggers

Update Context Map when:
1. New domain added
2. Integration pattern changes
3. Event subscriptions change
4. Shared entities added/removed
5. External system integration added

## Examples

### Adding a New Relationship

```markdown
## Context Map Update: Adding Payment Domain

**Change:** New Payment domain integrates with Contribution and User

**Relationships:**
- Payment ──Customer/Supplier──> Contribution
- Payment ──Customer/Supplier──> User
- Payment ──ACL──> Stripe API

**Integration:**
- Subscribes to: ContributionCreated event
- Depends on: User entity (for payment details)
- Publishes: PaymentSuccessful, PaymentFailed events

**ADR:** ADR-017 updated on 2026-02-14
```

### Refactoring a Relationship

```markdown
## Context Map Update: Auth/User Partnership → Customer/Supplier

**Before:** Auth ──Partnership──> User (tight coupling)

**After:** Auth ──Customer/Supplier──> User (loose coupling via events)

**Changes:**
- Removed: Direct UserService dependency in Auth
- Added: Auth subscribes to UserActivated event
- Impact: Auth handlers updated to react to events

**Justification:** Reduce coupling; enable independent evolution

**ADR:** ADR-017 updated on 2026-02-14
```

## References

- Eric Evans, *Domain-Driven Design Reference* (Context Mapping patterns)
- DDD Crew, Bounded Context Canvas: https://github.com/ddd-crew/bounded-context-canvas
- Vernon, *Implementing Domain-Driven Design* (Strategic design chapters)

[1]: https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf "Domain-Driven Design Reference"
