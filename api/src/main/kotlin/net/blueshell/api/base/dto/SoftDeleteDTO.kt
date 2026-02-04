package net.blueshell.api.base.dto

import net.blueshell.api.base.BaseDTO
import java.time.Instant

abstract class SoftDeleteDTO : BaseDTO() {
    open var deletedAt: Instant? = null
}
