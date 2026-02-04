package net.blueshell.api.base.dto

abstract class AuditedCustomIdDTO<ID> : AuditedSoftDeleteDTO(), IdentifiableDTO<ID> {
    override var id: ID? = null
}
