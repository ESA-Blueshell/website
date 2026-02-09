package net.blueshell.api.shared.dto

import java.time.Instant

abstract class AuditedSoftDeleteDTO : AuditedVersionedDTO() {
    var deletedAt: Instant? = null
}
