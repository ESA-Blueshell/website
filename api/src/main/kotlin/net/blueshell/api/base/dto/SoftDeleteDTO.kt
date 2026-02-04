package net.blueshell.api.base.dto

import java.time.Instant

abstract class SoftDeleteDTO : BaseDTO() {
    open var deletedAt: Instant? = null
}
