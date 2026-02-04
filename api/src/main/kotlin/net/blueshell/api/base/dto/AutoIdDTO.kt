package net.blueshell.api.base.dto

abstract class AutoIdDTO : BaseDTO(), IdentifiableDTO<Long> {
    override var id: Long? = null
}
