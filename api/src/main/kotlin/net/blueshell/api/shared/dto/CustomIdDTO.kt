package net.blueshell.api.shared.dto

abstract class CustomIdDTO<ID> : BaseDTO(), IdentifiableDTO<ID> {
    override var id: ID? = null
}
