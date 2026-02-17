package net.blueshell.api.shared.dto

import java.time.Instant

abstract class AuditedSoftDeleteDTO(
    var deletedAt: Instant? = null
) : AuditedVersionedDTO()
