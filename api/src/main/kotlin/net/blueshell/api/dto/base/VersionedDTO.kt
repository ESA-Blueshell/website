package net.blueshell.api.dto.base

abstract class VersionedDTO : BaseDTO() {
    var version: Long? = null
}
