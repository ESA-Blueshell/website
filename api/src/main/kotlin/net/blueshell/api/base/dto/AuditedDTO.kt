package net.blueshell.api.base.dto

import java.time.Instant

abstract class AuditedDTO : BaseDTO() {
    open var createdAt: Instant? = null
    open var updatedAt: Instant? = null
}
