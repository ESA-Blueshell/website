# ADR-019: Anti-Corruption Layers for External Integration

## Status
Accepted

## Context

The application integrates with external systems (Google Calendar, Brevo email, Mollie payments) whose models and APIs don't align with our domain model. Evans' **Anti-Corruption Layer (ACL)** explicitly recommends an isolating layer that provides upstream functionality in terms of the downstream model ([Domain Language][1]). Microsoft describes the ACL as a façade/adapter between a modern application and legacy/external systems ([Microsoft Learn][2]).

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
├── email/                  # Brevo ACL
│   ├── BrevoEmailAdapter.kt
│   ├── BrevoContactMapper.kt
│   └── templates/
├── calendar/               # Google Calendar ACL
│   ├── GoogleCalendarAdapter.kt
│   ├── EventMapper.kt
│   └── CalendarService.kt
└── payment/                # Mollie ACL
    ├── MolliePaymentAdapter.kt
    └── PaymentMapper.kt
```

### ACL Responsibilities

1. **Translation** - Convert domain models ↔ external API formats
2. **Protocol Adaptation** - Handle HTTP/REST specifics
3. **Data Normalization** - Clean/validate external data
4. **Error Translation** - Convert external errors to domain exceptions
5. **Protective Validation** - Verify external responses

## Guidelines

### DO:
- ✅ Place ACLs in `platform/integration/{system}/`
- ✅ Use adapter pattern with domain-friendly interfaces
- ✅ Translate at boundary (no external types in domain)
- ✅ Handle external API errors gracefully
- ✅ Mock ACLs in domain tests
- ✅ Version external API contracts

### DON'T:
- ❌ Let external models enter domain layer
- ❌ Scatter integration logic across domain
- ❌ Skip validation of external data
- ❌ Expose external API details to domain

## Examples

### Brevo Email ACL
```kotlin
// platform/integration/email/BrevoEmailAdapter.kt
@Component
class BrevoEmailAdapter(
    private val brevoClient: BrevoClient
) : EmailService {  // Domain interface

    override fun sendCampaign(campaign: EmailCampaign): Result<CampaignId> {
        return try {
            val brevoRequest = mapToBrevoFormat(campaign)
            val response = brevoClient.createCampaign(brevoRequest)
            Result.success(CampaignId(response.id))
        } catch (e: BrevoApiException) {
            Result.failure(EmailServiceException("Brevo API error", e))
        }
    }

    private fun mapToBrevoFormat(campaign: EmailCampaign): BrevoCreateCampaign {
        // Translation logic isolated here
    }
}
```

### Domain Uses ACL
```kotlin
// domain/event/application/EventNotificationService.kt
@Service
class EventNotificationService(
    private val emailService: EmailService  // ✅ Domain interface
) {
    fun notifyAttendees(event: Event) {
        val campaign = EmailCampaign(/* domain data */)
        emailService.sendCampaign(campaign)  // ✅ No Brevo knowledge
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
