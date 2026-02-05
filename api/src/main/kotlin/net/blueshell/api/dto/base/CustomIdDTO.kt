package net.blueshell.api.dto.base

abstract class CustomIdDTO<ID> : BaseDTO(), IdentifiableDTO<ID> {
    override var id: ID? = null
}
