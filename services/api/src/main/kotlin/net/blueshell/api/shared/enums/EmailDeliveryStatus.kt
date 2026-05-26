package net.blueshell.api.shared.enums

enum class EmailDeliveryStatus {
    PENDING,    // record created, not yet sent
    SENT,       // accepted by the SMTP relay
    DELIVERED,  // inferred from tracking pixel (OPENED implies DELIVERED)
    OPENED,     // recipient opened (tracking pixel fired)
    BOUNCED,    // hard or soft bounce (IMAP bounce poll)
    FAILED      // transport error (API unreachable / rejected)
}
