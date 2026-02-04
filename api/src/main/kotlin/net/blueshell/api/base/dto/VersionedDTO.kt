package net.blueshell.api.base.dto

import net.blueshell.api.base.BaseDTO

abstract class VersionedDTO : BaseDTO() {
    open var version: Long? = null
}
