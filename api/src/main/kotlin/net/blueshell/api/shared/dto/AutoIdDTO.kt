package net.blueshell.api.shared.dto

abstract class AutoIdDTO : BaseDTO(), IdentifiableDTO<Long> {
    override var id: Long? = null
}
