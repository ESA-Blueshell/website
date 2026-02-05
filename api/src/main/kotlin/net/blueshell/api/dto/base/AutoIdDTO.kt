package net.blueshell.api.dto.base

abstract class AutoIdDTO : BaseDTO(), IdentifiableDTO<Long> {
    override var id: Long? = null
}
