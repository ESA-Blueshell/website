package net.blueshell.api.dto.committee

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import net.blueshell.api.base.BaseDTO
@Schema(name = "AdvancedCommittee")
class AdvancedCommitteeDTO : BaseDTO() {
    @NotBlank(message = "Committee name cannot be blank.")
    @Size(max = 255, message = "Committee name cannot exceed 255 characters.")
    @JsonProperty("name")
    val name: @NotBlank(message = "Committee name cannot be blank.") @Size(
        max = 255,
        message = "Committee name cannot exceed 255 characters."
    ) String? = null

    @NotBlank(message = "Committee description cannot be empty.")
    @Size(max = 4095, message = "Committee description cannot exceed 4095 characters.")
    val description: @NotBlank(message = "Committee description cannot be empty.") @Size(
        max = 4095,
        message = "Committee description cannot exceed 4095 characters."
    ) String? = null

    @JsonProperty("members")
    @NotEmpty
    val members: @NotEmpty MutableList<CommitteeMemberDTO?>? = null
}
