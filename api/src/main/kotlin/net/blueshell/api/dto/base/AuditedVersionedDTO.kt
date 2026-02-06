package net.blueshell.api.dto.base

import java.time.Instant

abstract class AuditedVersionedDTO : VersionedDTO() {
    var createdAt: Instant? = null
    var updatedAt: Instant? = null
}
