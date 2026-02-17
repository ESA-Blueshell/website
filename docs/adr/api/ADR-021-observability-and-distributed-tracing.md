# ADR-021: Observability and Distributed Tracing

## Status
Proposed

## Context

Asynchronous event-driven architectures make debugging difficult without correlation identifiers. OpenTelemetry describes context propagation as enabling traces to build causal relationships across services via standard headers ([OpenTelemetry][1]). W3C Trace Context defines standard HTTP headers for propagating tracing context ([W3C][2]).

Current gaps:
- No trace context propagation for async events
- Difficult to correlate cross-domain operations
- Limited visibility into event workflows
- No standard correlation IDs

## Decision

We adopt **OpenTelemetry** standards for observability:

### Event Envelope Structure
```kotlin
data class DomainEventEnvelope<T>(
    val eventId: String = UUID.randomUUID().toString(),
    val correlationId: String,  // W3C trace-id
    val causationId: String?,   // Previous event ID
    val occurredAt: Instant = Instant.now(),
    val producerContext: ProducerMetadata,
    val payload: T
)

data class ProducerMetadata(
    val service: String = "blueshell-api",
    val version: String = "1.0.0",
    val domain: String
)
```

### Propagation Requirements

All cross-boundary calls must propagate:
- **Trace Context** (W3C Trace Context headers)
- **Correlation ID** (business workflow ID)
- **Causation ID** (triggering event ID)

## Guidelines

### DO:
- ✅ Propagate correlation IDs in event envelopes
- ✅ Log trace IDs with all operations
- ✅ Use OpenTelemetry SDK
- ✅ Include metadata in domain events

### DON'T:
- ❌ Create new correlation IDs mid-workflow
- ❌ Skip trace propagation in async operations

## Implementation Status

**Proposed** - Not yet implemented. Future enhancement.

## References
- OpenTelemetry, context propagation ([OpenTelemetry][1])
- W3C, Trace Context specification ([W3C][2])

[1]: https://opentelemetry.io/docs/concepts/context-propagation/
[2]: https://www.w3.org/TR/trace-context/
