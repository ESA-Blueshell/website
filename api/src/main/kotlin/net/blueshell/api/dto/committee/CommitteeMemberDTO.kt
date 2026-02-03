package net.blueshell.api.dto.committee

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.BaseDTO
@Schema(name = "CommitteeMember")
class CommitteeMemberDTO : BaseDTO() {
    @NotBlank
    val userId: @NotBlank Long? = null
    val committeeId: Long? = null

    @NotBlank
    val role: @NotBlank String? = null
}
