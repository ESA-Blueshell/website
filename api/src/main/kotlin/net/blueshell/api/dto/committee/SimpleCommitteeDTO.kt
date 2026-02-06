package net.blueshell.api.dto.committee

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.dto.base.AuditedAutoIdDTO

@Schema(name = "SimpleCommittee")
data class SimpleCommitteeDTO(
    var name: String? = null,
    var description: String? = null
) : AuditedAutoIdDTO()
