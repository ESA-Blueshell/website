package net.blueshell.api.dto.base

import java.time.Instant

abstract class AuditedSoftDeleteDTO : AuditedVersionedDTO() {
    open var deletedAt: Instant? = null
}
