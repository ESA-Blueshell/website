package net.blueshell.api.dto.committee

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.dto.base.AuditedAutoIdDTO

@Schema(name = "SimpleCommittee")
data class SimpleCommitteeDTO(
    @field:JsonProperty("name")
    var name: String? = null,

    @field:JsonProperty("description")
    var description: String? = null
) : AuditedAutoIdDTO()
