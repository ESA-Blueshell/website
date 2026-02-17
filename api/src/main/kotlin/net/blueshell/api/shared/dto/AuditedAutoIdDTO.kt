package net.blueshell.api.shared.dto

abstract class AuditedAutoIdDTO(
    override var id: Long? = null
) : AuditedSoftDeleteDTO(), IdentifiableDTO<Long>
