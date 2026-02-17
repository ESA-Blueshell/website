package net.blueshell.api.shared.dto

import java.time.Instant

abstract class AuditedVersionedDTO(
    var createdAt: Instant? = null,
    var updatedAt: Instant? = null
) : VersionedDTO()
