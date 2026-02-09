package net.blueshell.api.shared.dto

import java.time.Instant

abstract class AuditedVersionedDTO : VersionedDTO() {
    var createdAt: Instant? = null
    var updatedAt: Instant? = null
}
