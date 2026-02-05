package net.blueshell.api.dto.base

import java.time.Instant

abstract class SoftDeleteDTO : BaseDTO() {
    open var deletedAt: Instant? = null
}
