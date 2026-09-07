# ADR-019: Anti-Corruption Layers for External Integration

## Status
Accepted. Inventory corrected on 2026-09-07 against the code (#1196).

## Context

The application integrates with external systems — Google Calendar, Brevo, Discord, and an SMTP server — whose models and APIs do not align with our domain model. Evans' **Anti-Corruption Layer (ACL)** explicitly recommends an isolating layer that provides upstream functionality in terms of the downstream model ([Domain Language][1]). Microsoft describes the ACL as a façade/adapter between a modern application and legacy/external systems ([Microsoft Learn][2]).

Direct integration risks:
- Domain model pollution (external concepts leak into domain)
- Coupling to external API structure
- Hard to replace external services
- Tests depend on external systems

## Decision

We implement an **Anti-Corruption Layer** for every external system.

An ACL lives in the module that owns the concern, beside the domain it protects, rather than in one integration package: contact sync is `contact`'s business and calendar sync is `sync`'s, so that is where their adapters are. `platform/integration/` holds the mocks the test and dev profiles use, and nothing else.

### Where the layers are

```
email/domain/
├── EmailTransportClient.kt      # the domain interface
└── SmtpEmailClient.kt           # SMTP through Spring's JavaMailSender, every non-test profile
contact/
├── api/ContactAdapter.kt        # the domain interface
├── api/BrevoContactAdapter.kt   # @Profile("!test & !dev")
└── domain/BrevoListAdapter.kt   # @Profile("!test & !dev"), lists rather than contacts
sync/domain/
├── GoogleCalendarAdapter.kt     # @Profile("!test & !dev")
├── GoogleCalendarClient.kt
├── BrevoContactSyncTarget.kt    # @Profile("!test & !dev")
└── DiscordClientConfig.kt       # @Profile("!test & !dev"), wires the published client
platform/integration/mock/
├── InMemoryEmailClient.kt       # @Primary @Profile("test")
├── MockContactAdapter.kt        # @Primary @Profile("test | dev")
├── MockCalendarAdapter.kt       # @Primary @Profile("test | dev")
├── MockBrevoContactSyncTarget.kt
├── MockGoogleCalendarSyncTarget.kt
└── MockTargetStrategy.kt
```

**Email is SMTP, not Listmonk.** The Listmonk transport and its contact adapter are gone; this ADR described both until #1196. `SmtpEmailClient` generates the `Message-ID` itself so the outbox row and the MIME header carry the same value, which is what lets the bounce poller match a DSN to what it answers.

**Discord is wired but consumed by nothing yet.** `DiscordClientConfig` builds a `DiscordApi` from the published `net.blueshell.clients:discord-client`, in production only. No adapter reads it, so there is no translation layer to describe — when one arrives it belongs beside the others and in this list.

**There is no payment integration.** An ACL for the Mollie Payment API was listed here and in ADR-017 with a location reading "if exists". `Mollie` appears in no file under `services/api/src/main`.

### ACL Responsibilities

1. **Translation** — Convert domain models ↔ external API formats
2. **Protocol Adaptation** — Handle HTTP/REST specifics
3. **Data Normalization** — Clean/validate external data
4. **Error Translation** — Convert external errors to domain exceptions
5. **Protective Validation** — Verify external responses

### One interface, more than one system

Where several external systems serve the same purpose, each gets its own adapter behind one
domain interface. Contact sync is the case that exists:

```kotlin
// contact/api/ContactAdapter.kt — the domain interface
interface ContactAdapter {
    val system: TargetSystem

    fun createContact(data: ContactData): Long

    /** Returns the current external id, which an adapter may rewrite when repairing stale pairing. */
    fun updateContact(externalId: Long, data: ContactData): Long

    fun deleteContact(externalId: Long)
}

// contact/api/BrevoContactAdapter.kt — production only
@Service
@Profile("!test & !dev")
class BrevoContactAdapter(...) : ContactAdapter { ... }
```

`updateContact` returning an id is the ACL earning its place: Brevo can answer an update by
moving the contact, and the domain would otherwise keep a pairing the external system has
already abandoned. The interface says so; the adapter absorbs it.

### Profile conventions

A production adapter declares a `@Profile`, so nothing reaches a real system from a test:

| Adapter | `@Profile` | Why |
|---|---|---|
| `SmtpEmailClient` | `!test` | Dev sends through the local mail container; tests send nothing |
| `BrevoContactAdapter` | `!test & !dev` | Production only |
| `BrevoListAdapter` | `!test & !dev` | Production only |
| `GoogleCalendarAdapter` | `!test & !dev` | Production only |
| `BrevoContactSyncTarget` | `!test & !dev` | Production only |
| `DiscordClientConfig` | `!test & !dev` | Production only; wires the client, and nothing reads it yet |

A mock declares `@Primary` and the profiles it stands in for:

| Mock | `@Profile` | Stands in for |
|---|---|---|
| `InMemoryEmailClient` | `test` | `SmtpEmailClient` |
| `MockContactAdapter` | `test \| dev` | `BrevoContactAdapter` |
| `MockCalendarAdapter` | `test \| dev` | `GoogleCalendarAdapter` |
| `MockBrevoContactSyncTarget` | `test \| dev` | `BrevoContactSyncTarget` |
| `MockGoogleCalendarSyncTarget` | `test \| dev` | the calendar sync target |
| `MockTargetStrategy` | `test \| dev` | the target strategy the fan-out reads |

## Guidelines

### DO:
- ✅ Place ACLs in `platform/integration/{system}/`
- ✅ Use adapter pattern with domain-friendly interfaces defined in `shared/`
- ✅ Annotate production adapters with `@Profile` (e.g., `@Profile("!test")`)
- ✅ Annotate mock adapters with `@Primary` and a test/dev profile
- ✅ Translate at boundary (no external types in domain)
- ✅ Handle external API errors gracefully (wrap in domain exceptions)
- ✅ Use `List<AdapterInterface>` injection for fan-out across multiple systems

### DON'T:
- ❌ Let external models enter domain layer
- ❌ Scatter integration logic across domain
- ❌ Skip validation of external data
- ❌ Expose external API details to domain
- ❌ Omit `@Profile` from production adapters (causes test pollution)
- ❌ Omit `@Primary` from mock adapters (causes `NoUniqueBeanDefinitionException`)

## Examples

### The email ACL

```kotlin
// email/domain/SmtpEmailClient.kt
@Component
@Profile("!test")
class SmtpEmailClient(private val mailSender: JavaMailSender) : EmailTransportClient {
    override fun send(...): String {
        val messageId = "<${UUID.randomUUID()}@blueshell.utwente.nl>"
        // ... build the MIME message, set Message-ID, send
        return messageId
    }
}
```

The id is generated here rather than read back from the server, so the outbox row and the header
carry the same value and a bounce can be matched to what it answers. That is translation the
domain does not have to know about, which is the point of the layer.

### Mock Adapter Pattern
```kotlin
// platform/integration/mock/MockContactAdapter.kt
@Service
@Primary
@Profile("test | dev")
class MockContactAdapter : ContactSyncAdapter, ListSyncAdapter {
    override val system = ContactSystem.LISTMONK

    private val contacts = ConcurrentHashMap<Long, MockContact>()

    override fun createContact(data: ContactData): Long { /* in-memory */ }
    override fun updateContact(externalId: Long, data: ContactData): Long { /* in-memory */ }
    override fun deleteContact(externalId: Long) { /* in-memory */ }

    // Test inspection helpers
    fun getAllContacts(): Map<Long, MockContact> = contacts.toMap()
    fun clear() = contacts.clear()
}
```

### Domain Uses ACL
```kotlin
// event/domain/EventEmailListener.kt
@Component
class EventEmailListener(
    private val emailService: EmailService  // the domain's own interface, not a transport
) {
    @EventListener
    fun onSignUpCreated(event: SignUpCreated) {
        val content = buildSignupEmail(event)
        emailService.sendEmail(content)  // nothing here knows how a message leaves the building
    }
}
```

## References
- Eric Evans, "Anti-corruption Layer" in DDD Reference ([Domain Language][1])
- Microsoft, Anti-corruption Layer pattern ([Microsoft Learn][2])
- Microservices.io, ACL pattern ([microservices.io][3])

[1]: https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf
[2]: https://learn.microsoft.com/en-us/azure/architecture/patterns/anti-corruption-layer
[3]: https://microservices.io/patterns/refactoring/anti-corruption-layer.html
