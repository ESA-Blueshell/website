package net.blueshell.api.dto.base

import java.time.Instant

abstract class AuditedSoftDeleteDTO : AuditedVersionedDTO() {
    var deletedAt: Instant? = null
}
