package net.blueshell.api.dto.base

abstract class VersionedDTO : BaseDTO() {
    open var version: Long? = null
}
