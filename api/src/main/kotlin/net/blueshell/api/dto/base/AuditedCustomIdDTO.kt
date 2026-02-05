package net.blueshell.api.dto.base

abstract class AuditedCustomIdDTO<ID> : AuditedSoftDeleteDTO(), IdentifiableDTO<ID> {
    override var id: ID? = null
}
