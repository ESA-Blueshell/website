package net.blueshell.api.shared.dto

abstract class AuditedCustomIdDTO<ID> : AuditedSoftDeleteDTO(), IdentifiableDTO<ID> {
    override var id: ID? = null
}
