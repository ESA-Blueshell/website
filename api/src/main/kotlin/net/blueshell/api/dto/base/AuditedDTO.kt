package net.blueshell.api.dto.base

import java.time.Instant

abstract class AuditedDTO : BaseDTO() {
    var createdAt: Instant? = null
    var updatedAt: Instant? = null
}
