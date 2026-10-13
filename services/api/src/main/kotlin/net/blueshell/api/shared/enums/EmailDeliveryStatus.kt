package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class EmailDeliveryStatus {
    /** Its job is queued; nothing has been sent yet. */
    QUEUED,

    /** Accepted by the SMTP relay. */
    SENT,

    /** Inferred from the tracking pixel: an open implies a delivery. */
    DELIVERED,

    /** The recipient opened it and the tracking pixel fired. */
    OPENED,

    /** A hard or soft bounce, found by the IMAP bounce poll. */
    BOUNCED,

    /** A transport error: the API was unreachable or rejected the send. */
    FAILED,
}
