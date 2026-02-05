package net.blueshell.api.dto.base

abstract class AuditedAutoIdDTO : AuditedSoftDeleteDTO(), IdentifiableDTO<Long> {
    override var id: Long? = null
}
