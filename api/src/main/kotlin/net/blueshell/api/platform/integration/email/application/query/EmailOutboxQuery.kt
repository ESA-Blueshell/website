package net.blueshell.api.platform.integration.email.application.query

import net.blueshell.api.shared.enums.EmailDeliveryStatus

data class EmailOutboxQuery(
    var deliveryStatus: EmailDeliveryStatus? = null,
    var emailType: String? = null,
    var search: String? = null,
)
