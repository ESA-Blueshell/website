package net.blueshell.api.shared.enums

enum class EmailDeliveryStatus {
    PENDING,    // record created, not yet sent
    SENT,       // accepted by Brevo, awaiting events
    DELIVERED,  // confirmed delivery to inbox
    OPENED,     // recipient opened (tracking pixel fired)
    BOUNCED,    // hard or soft bounce
    FAILED      // transport error (Brevo API unreachable / rejected)
}
