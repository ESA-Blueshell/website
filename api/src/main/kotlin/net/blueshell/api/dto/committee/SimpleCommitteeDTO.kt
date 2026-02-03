package net.blueshell.api.dto.committee

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
@Schema(name = "SimpleCommittee")
class SimpleCommitteeDTO : BaseDTO() {
    @JsonProperty("name")
    val name: String? = null

    @JsonProperty("description")
    val description: String? = null
}
