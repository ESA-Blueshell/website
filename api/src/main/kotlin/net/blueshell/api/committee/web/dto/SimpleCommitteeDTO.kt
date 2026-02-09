package net.blueshell.api.committee.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "SimpleCommittee")
data class SimpleCommitteeDTO(
    var name: String,
    var description: String,
) : AuditedAutoIdDTO()
