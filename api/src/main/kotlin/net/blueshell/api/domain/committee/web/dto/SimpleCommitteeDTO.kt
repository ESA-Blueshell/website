package net.blueshell.api.domain.committee.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "SimpleCommittee")
data class SimpleCommitteeDTO(
    @field:NotBlank
    @field:Size(max = 255)
    var name: String? = null,

    @field:NotBlank
    @field:Size(max = 4095)
    var description: String? = null,
) : AuditedAutoIdDTO()
