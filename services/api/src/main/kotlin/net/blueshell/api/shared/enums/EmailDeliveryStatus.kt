package net.blueshell.api.shared.enums

enum class EmailDeliveryStatus {
    PENDING,    // record created, not yet sent
    SENT,       // accepted by Listmonk transactional API
    DELIVERED,  // inferred from tracking pixel (OPENED implies DELIVERED)
    OPENED,     // recipient opened (tracking pixel fired)
    BOUNCED,    // hard or soft bounce (Listmonk bounce poll)
    FAILED      // transport error (API unreachable / rejected)
}
