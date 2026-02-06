package net.blueshell.api.dto.base

import java.time.Instant

abstract class SoftDeleteDTO : BaseDTO() {
    var deletedAt: Instant? = null
}
