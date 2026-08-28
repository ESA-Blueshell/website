package net.blueshell.api.email.domain

import net.blueshell.api.shared.enums.EmailDeliveryStatus

data class EmailQuery(
    var deliveryStatus: EmailDeliveryStatus? = null,
    var emailType: String? = null,
    var search: String? = null,
)
