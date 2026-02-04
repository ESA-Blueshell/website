package net.blueshell.api.base.dto

abstract class AuditedAutoIdDTO : AuditedSoftDeleteDTO(), IdentifiableDTO<Long> {
    override var id: Long? = null
}
