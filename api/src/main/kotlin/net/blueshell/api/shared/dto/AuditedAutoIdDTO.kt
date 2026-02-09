package net.blueshell.api.shared.dto

abstract class AuditedAutoIdDTO : AuditedSoftDeleteDTO(), IdentifiableDTO<Long> {
    override var id: Long? = null
}
