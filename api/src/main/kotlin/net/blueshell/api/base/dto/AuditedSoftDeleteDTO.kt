package net.blueshell.api.base.dto

import java.time.Instant

abstract class AuditedSoftDeleteDTO : AuditedVersionedDTO() {
    open var deletedAt: Instant? = null
}
