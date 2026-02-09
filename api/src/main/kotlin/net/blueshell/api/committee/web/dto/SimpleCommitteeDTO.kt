package net.blueshell.api.committee.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "SimpleCommittee")
data class SimpleCommitteeDTO(
    var name: String? = null,
    var description: String? = null
) : AuditedAutoIdDTO()
