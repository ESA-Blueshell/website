package net.blueshell.api.dto.committee

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.BaseDTO

@Schema(name = "CommitteeMember")
data class CommitteeMemberDTO(
    @field:NotBlank
    var userId: Long? = null,

    var committeeId: Long? = null,

    @field:NotBlank
    var role: String? = null
) : BaseDTO()
