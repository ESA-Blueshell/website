package net.blueshell.api.shared.dto

import java.time.Instant

abstract class AuditedDTO : BaseDTO() {
    var createdAt: Instant? = null
    var updatedAt: Instant? = null
}
