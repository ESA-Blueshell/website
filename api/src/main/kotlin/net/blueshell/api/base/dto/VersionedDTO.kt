package net.blueshell.api.base.dto

abstract class VersionedDTO : BaseDTO() {
    open var version: Long? = null
}
