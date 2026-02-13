# ADR-006: Event-Driven Architecture

## Status
Accepted

## Context
Applications need to react to state changes across different domains without creating tight coupling. Traditional approaches include:
- Direct service-to-service calls (tight coupling)
- Callbacks (hard to track, test)
- Observer pattern (limited to in-process)
- Message queues (infrastructure overhead)

Requirements:
- Decouple domains from each other
- Enable side effects without polluting business logic
- Support audit logging, notifications, integrations
- Keep transactions consistent
- Allow async processing where appropriate

## Decision
We adopt **Spring's event-driven architecture** with domain events for cross-domain communication and side effects.

### Event Types

**Domain Events**
- Represent something that happened in the domain
- Past tense naming (UserCreated, PasswordChanged)
- Immutable data classes
- Located in `domain/{domain-name}/application/event/`
- Example: `domain/user/application/event/UserCreated.kt`

**Integration Events**
- For external system integration
- May be published to message queues
- Include all data needed by external systems
- Located in shared infrastructure or domain application layer

### Event Organization

**Package Structure:**
```
domain/{domain-name}/
├── application/
│   ├── event/              # Domain events
│   │   ├── UserCreated.kt
│   │   ├── UserUpdated.kt
│   │   └── UserDeleted.kt
│   └── listener/           # Event listeners
│       └── UserEventListener.kt
```

**Listeners** are placed in the domain that reacts to events, not necessarily where events are published:
- `domain/user/application/event/UserCreated.kt` - event definition
- `domain/auth/application/listener/RecoveryEventListener.kt` - listens to UserCreated

### Event Structure

```kotlin
// Domain event
data class UserCreated(
    val userId: Long,
    val createdByBoard: Boolean? = null
) : DomainEvent

// Integration event
data class EmailRequested(
    val to: String,
    val subject: String,
    val body: String,
    val templateId: String? = null
)
```

### Publishing Events

**From Services:**
```kotlin
@Service
class UserService(
    private val repository: UserRepository,
    private val events: AfterCommitEventPublisher
) {

    @Transactional
    override fun create(entity: User): User {
        val saved = super.create(entity)
        events.publish(UserCreated(saved.id!!, createdByBoard = isBoardUser()))
        return saved
    }

    @Transactional
    fun activateUser(userId: Long) {
        val user = findById(userId)
        user.enabled = true
        update(user)
        events.publish(UserActivated(userId))
    }
}
```

**From Command Handlers:**
```kotlin
@Component
class SetPasswordHandler(
    private val passwordRecoveryService: PasswordRecoveryService,
    private val events: EventPublisher
) : CommandHandler<SetPasswordCommand, Unit> {

    @Transactional
    override fun handle(command: SetPasswordCommand) {
        passwordRecoveryService.setPassword(command.token, command.password)
        events.publish(PasswordChanged(userId = ...))
    }
}
```

### Listening to Events

**Synchronous Listener (Same Transaction):**
```kotlin
@Component
class RecoveryEventListener(
    private val jobDispatcher: JobDispatcher,
    private val activationService: UserActivationService
) {

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(event: UserCreated) {
        val dispatch = activationService.issueActivationForNewUser(
            event.userId,
            event.createdByBoard == true
        )

        jobDispatcher.enqueue(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
        )
    }
}
```

**Async Listener:**
```kotlin
@Component
class AuditEventListener(
    private val auditService: AuditService
) {

    @Async
    @EventListener
    fun onUserCreated(event: UserCreated) {
        auditService.log(
            action = "USER_CREATED",
            entityId = event.userId,
            timestamp = Instant.now()
        )
    }
}
```

### Event Publisher

**AfterCommitEventPublisher** (for transactional events):
```kotlin
@Component
class AfterCommitEventPublisher(
    private val publisher: ApplicationEventPublisher
) {

    fun publish(event: Any) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    publisher.publishEvent(event)
                }
            }
        )
    }
}
```

**Benefits:**
- Events only published if transaction commits
- No events on rollback
- Consistent state

## Consequences

### Positive
- **Decoupling**: Domains don't depend on each other
- **Extensibility**: Add listeners without modifying publishers
- **Testability**: Can test publishers and listeners independently
- **Auditability**: All domain changes publishable as events
- **Async processing**: Listeners can be async
- **Transactional safety**: AfterCommit ensures consistency
- **Discoverability**: Events document what happens in system
- **Side effects**: Clean separation of main logic from side effects

### Negative
- **Eventual consistency**: Async listeners may lag
- **Debugging complexity**: Event flow harder to trace
- **Testing complexity**: Must set up event infrastructure
- **Ordering issues**: Event order not guaranteed
- **Error handling**: Listener failures need careful handling

### Trade-offs
- **Coupling vs Complexity**: Less coupling but more moving parts
- **Consistency vs Performance**: Sync for consistency, async for performance

## Guidelines

### Event Naming
- **Past tense**: UserCreated, PasswordChanged, EventPublished
- **Domain language**: Use ubiquitous language
- **Specific**: Avoid generic names like EntityModified
- **Include entity**: UserCreated not just Created

### What to Include in Events
```kotlin
// ✅ GOOD: Minimal, immutable data
data class UserCreated(
    val userId: Long,
    val createdByBoard: Boolean? = null
)

// ❌ BAD: Full entity (causes coupling)
data class UserCreated(
    val user: User  // ❌ Tight coupling to entity
)

// ❌ BAD: Mutable
data class UserCreated(
    var userId: Long  // ❌ Mutable
)
```

### Listener Design

**DO:**
- ✅ Keep listeners small and focused
- ✅ Use `@Transactional(propagation = REQUIRES_NEW)` for new transactions
- ✅ Handle failures gracefully
- ✅ Use async for non-critical operations
- ✅ Document expected event order (if critical)
- ✅ Use specific event types

**DON'T:**
- ❌ Put business logic in listeners (use services)
- ❌ Create long listener chains (hard to debug)
- ❌ Depend on event order (unless documented)
- ❌ Throw unhandled exceptions from async listeners
- ❌ Access HTTP request in async listeners
- ❌ Make external API calls in sync listeners

### Transaction Propagation

**REQUIRES_NEW** (most common):
```kotlin
@EventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun onUserCreated(event: UserCreated) {
    // New transaction, independent of publisher
}
```

**REQUIRED** (joins publisher's transaction):
```kotlin
@EventListener
@Transactional(propagation = Propagation.REQUIRED)
fun onUserCreated(event: UserCreated) {
    // Same transaction as publisher
    // Rollback affects both
}
```

**NOT_SUPPORTED** (no transaction):
```kotlin
@EventListener
@Transactional(propagation = Propagation.NOT_SUPPORTED)
fun onUserCreated(event: UserCreated) {
    // No transaction
}
```

### Error Handling

**Sync Listeners:**
```kotlin
@EventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun onUserCreated(event: UserCreated) {
    try {
        // Critical operation
        sendActivationEmail(event.userId)
    } catch (e: Exception) {
        logger.error("Failed to send activation email", e)
        // Decide: throw (rollback) or log (continue)
    }
}
```

**Async Listeners:**
```kotlin
@Async
@EventListener
fun onUserCreated(event: UserCreated) {
    try {
        // Non-critical operation
        updateStatistics(event.userId)
    } catch (e: Exception) {
        logger.error("Failed to update statistics", e)
        // Don't throw, would kill executor thread
    }
}
```

## Testing Events

**Testing Event Publication:**
```kotlin
@SpringBootTest
class UserServiceTest {

    @Autowired
    lateinit var userService: UserService

    @MockBean
    lateinit var eventPublisher: AfterCommitEventPublisher

    @Test
    fun `should publish UserCreated event on create`() {
        val user = User().apply {
            username = "testuser"
            email = "test@example.com"
        }

        userService.create(user)

        verify(eventPublisher).publish(
            argThat<UserCreated> { it.userId == user.id }
        )
    }
}
```

**Testing Event Listeners:**
```kotlin
@SpringBootTest
class RecoveryEventListenerTest {

    @Autowired
    lateinit var listener: RecoveryEventListener

    @MockBean
    lateinit var activationService: UserActivationService

    @Test
    fun `should issue activation token on UserCreated event`() {
        val event = UserCreated(userId = 1L, createdByBoard = false)

        listener.onUserCreated(event)

        verify(activationService).issueActivationForNewUser(1L, false)
    }
}
```

## Best Practices

### DO:
- ✅ Use AfterCommitEventPublisher for transactional events
- ✅ Keep events immutable
- ✅ Use past tense for event names
- ✅ Include only necessary data in events
- ✅ Document event contracts
- ✅ Handle listener exceptions appropriately
- ✅ Use async for non-critical operations
- ✅ Log event processing for debugging

### DON'T:
- ❌ Publish events before commit
- ❌ Include full entities in events
- ❌ Depend on listener execution order
- ❌ Put business logic directly in listeners
- ❌ Create circular event dependencies
- ❌ Ignore listener failures
- ❌ Use events for synchronous request/response

## Examples

### Complete Event Flow

**1. Define Event:**
```kotlin
data class UserCreated(
    val userId: Long,
    val createdByBoard: Boolean? = null
)
```

**2. Publish from Service:**
```kotlin
@Service
class UserService(
    private val repository: UserRepository,
    private val events: AfterCommitEventPublisher
) {
    @Transactional
    override fun create(entity: User): User {
        val saved = repository.save(entity)
        events.publish(UserCreated(saved.id!!, createdByBoard = isBoardUser()))
        return saved
    }
}
```

**3. Listen and React:**
```kotlin
@Component
class RecoveryEventListener(
    private val jobDispatcher: JobDispatcher,
    private val activationService: UserActivationService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(event: UserCreated) {
        val dispatch = activationService.issueActivationForNewUser(
            event.userId,
            event.createdByBoard == true
        )

        jobDispatcher.enqueue(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
        )
    }
}
```

**4. Audit Logging:**
```kotlin
@Component
class AuditEventListener(private val auditService: AuditService) {
    @Async
    @EventListener
    fun onUserCreated(event: UserCreated) {
        auditService.log("USER_CREATED", event.userId)
    }

    @Async
    @EventListener
    fun onPasswordChanged(event: PasswordChanged) {
        auditService.log("PASSWORD_CHANGED", event.userId)
    }
}
```

## Future Enhancements

### Transactional Outbox Pattern

For distributed event publishing (e.g., to RabbitMQ, Kafka), consider the **Transactional Outbox pattern** ([microservices.io][4]):

```kotlin
// Write event to outbox table in same transaction
@Transactional
fun publishEvent(event: DomainEvent) {
    // 1. Save domain state
    repository.save(entity)

    // 2. Write event to outbox table (same transaction)
    outboxRepository.save(OutboxMessage(
        aggregateId = entity.id,
        eventType = event::class.simpleName,
        payload = json.encode(event),
        createdAt = Instant.now()
    ))
}

// Separate process publishes from outbox
@Scheduled(fixedDelay = 1000)
fun processOutbox() {
    val pending = outboxRepository.findPendingMessages()
    pending.forEach { message ->
        messageBroker.publish(message.payload)
        outboxRepository.markAsPublished(message.id)
    }
}
```

**Benefits:**
- Guarantees at-least-once event delivery
- No dual-write problem (event publishing + state change in one transaction)
- Reliable integration with external message brokers

**See Also:**
- ADR-017: Bounded Context Relationships and Context Map
- ADR-018: Data Ownership in Modular Monolith

## References
- Spring Framework Events Documentation
- Domain-Driven Design: Domain Events
- Event Sourcing Pattern
- CQRS Pattern
- Transaction Synchronization
- Microservices.io, Transactional Outbox pattern ([microservices.io][4])

[4]: https://microservices.io/patterns/data/transactional-outbox.html "Pattern: Transactional outbox"
