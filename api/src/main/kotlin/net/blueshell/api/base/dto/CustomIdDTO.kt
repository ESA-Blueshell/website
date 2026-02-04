package net.blueshell.api.base.dto

abstract class CustomIdDTO<ID> : BaseDTO(), IdentifiableDTO<ID> {
    override var id: ID? = null
}
