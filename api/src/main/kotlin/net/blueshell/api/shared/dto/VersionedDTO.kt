package net.blueshell.api.shared.dto

import jakarta.validation.constraints.NotNull

abstract class VersionedDTO : BaseDTO() {
    @field:NotNull
    var version: Long? = null
}
