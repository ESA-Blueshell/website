# ADR-019: Anti-Corruption Layers for External Integration

## Status
Accepted

## Context

The application integrates with external systems (Google Calendar, Brevo email, Listmonk, Mollie payments) whose models and APIs don't align with our domain model. Evans' **Anti-Corruption Layer (ACL)** explicitly recommends an isolating layer that provides upstream functionality in terms of the downstream model ([Domain Language][1]). Microsoft describes the ACL as a façade/adapter between a modern application and legacy/external systems ([Microsoft Learn][2]).

Direct integration risks:
- Domain model pollution (external concepts leak into domain)
- Coupling to external API structure
- Hard to replace external services
- Tests depend on external systems

## Decision

We implement **Anti-Corruption Layers** for all external system integrations located in `platform/integration/`:

### ACL Structure
```
platform/integration/
├── email/                  # Email delivery ACL (Listmonk)
│   ├── ListmonkEmailClient.kt      # @Profile("!test") — production implementation
│   ├── EmailTransportClient.kt     # Domain interface
│   └── ...
├── calendar/               # Google Calendar ACL
│   ├── GoogleCalendarAdapter.kt    # @Profile("!test & !dev")
│   ├── GoogleCalendarClient.kt
│   └── job/SyncEventToCalendarJob.kt
├── contact/                # Contact sync ACL (Listmonk primary, Brevo secondary)
│   ├── ListmonkContactAdapter.kt   # @Profile("!test")
│   └── BrevoContactAdapter.kt      # @Profile("!test & !dev")
└── mock/                   # Test/dev mock adapters
    ├── MockContactAdapter.kt       # @Primary @Profile("test | dev")
    ├── MockCalendarAdapter.kt      # @Primary @Profile("test | dev")
    └── MockListmonkEmailClient.kt  # @Primary @Profile("test")
```

### ACL Responsibilities

1. **Translation** — Convert domain models ↔ external API formats
2. **Protocol Adaptation** — Handle HTTP/REST specifics
3. **Data Normalization** — Clean/validate external data
4. **Error Translation** — Convert external errors to domain exceptions
5. **Protective Validation** — Verify external responses

### Multi-Adapter Pattern

When multiple external systems serve the same logical purpose (e.g., Listmonk + Brevo both
handle contact sync), implement separate adapters behind the same domain interface:

```kotlin
// Domain interface (shared/)
interface ContactSyncAdapter {
    val system: ContactSystem
    fun createContact(data: ContactData): Long
    fun updateContact(systemContactId: Long, data: ContactData)
    fun deleteContact(systemContactId: Long)
}

// Primary adapter (Listmonk — active in all non-test environments)
@Service
@Profile("!test")
class ListmonkContactAdapter(
    private val subscribersApi: SubscribersApi,
    private val listsApi: ListsApi,
) : ContactSyncAdapter, ListSyncAdapter {
    override val system = ContactSystem.LISTMONK
    // ...
}

// Secondary adapter (Brevo — production only, not dev)
@Service
@Profile("!test & !dev")
class BrevoContactAdapter(
    private val brevoClient: BrevoContactClient
) : ContactSyncAdapter, ListSyncAdapter {
    override val system = ContactSystem.BREVO
    // ...
}
```

**Fan-out** (calling all registered adapters): inject `List<ContactSyncAdapter>` and iterate.

**Single injection** (active adapter only): when only one adapter is active per profile, Spring
resolves ambiguity automatically since at most one bean is in scope.

**Mock override**: `@Primary` on mock adapters ensures they win over production adapters when
both are on the classpath (test profile activates mock, `@Primary` resolves the bean conflict).

### Profile Conventions

All production adapters **must** declare `@Profile` to prevent activation in test environments:

| Adapter | `@Profile` | Rationale |
|---------|-----------|-----------|
| `ListmonkContactAdapter` | `!test` | Active in dev (real Listmonk) and prod |
| `BrevoContactAdapter` | `!test & !dev` | Production only |
| `GoogleCalendarAdapter` | `!test & !dev` | Production only |
| `ListmonkEmailClient` | `!test` | Active in dev and prod |

All mock adapters **must** declare `@Primary` and target `test` or `dev` profiles:

| Mock | `@Profile` | `@Primary` | Rationale |
|------|-----------|-----------|-----------|
| `MockContactAdapter` | `test \| dev` | ✅ | Overrides Listmonk+Brevo in safe environments |
| `MockCalendarAdapter` | `test \| dev` | ✅ | Overrides Google Calendar |
| `MockListmonkEmailClient` | `test` | ✅ | Overrides Listmonk email client in tests |

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

### Listmonk Email ACL
```kotlin
// platform/integration/email/ListmonkEmailClient.kt
@Component
@Profile("!test")
class ListmonkEmailClient(
    private val transactionalApi: TransactionalApi,
    @Qualifier(ListmonkConfig.TEMPLATE_ID_BEAN) private val templateId: Int,
) : EmailTransportClient {

    override fun send(
        toEmail: String, toName: String, subject: String,
        htmlContent: String, senderName: String,
        senderAddress: String, replyToAddress: String,
    ): String {
        val messageId = "<${UUID.randomUUID()}@listmonk>"
        // ... send via Listmonk transactional API
        return messageId
    }
}
```

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
    override fun updateContact(systemContactId: Long, data: ContactData) { /* in-memory */ }
    override fun deleteContact(systemContactId: Long) { /* in-memory */ }

    // Test inspection helpers
    fun getAllContacts(): Map<Long, MockContact> = contacts.toMap()
    fun clear() = contacts.clear()
}
```

### Domain Uses ACL
```kotlin
// domain/event/application/listener/EventEmailListener.kt
@Component
class EventEmailListener(
    private val emailService: EmailService  // ✅ Domain interface, not Listmonk
) {
    @EventListener
    fun onSignUpCreated(event: SignUpCreated) {
        val content = buildSignupEmail(event)
        emailService.sendEmail(content)  // ✅ No Listmonk knowledge
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
