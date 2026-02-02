package net.blueshell.api.dto.committee

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "SimpleCommittee")
class SimpleCommitteeDTO : BaseDTO() {
    @JsonProperty("id")
    private val id: Long? = null

    @JsonProperty("name")
    private val name: String? = null

    @JsonProperty("description")
    private val description: String? = null
}
