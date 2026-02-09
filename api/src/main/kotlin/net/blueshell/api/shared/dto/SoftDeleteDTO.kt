package net.blueshell.api.shared.dto

import java.time.Instant

abstract class SoftDeleteDTO : BaseDTO() {
    var deletedAt: Instant? = null
}
