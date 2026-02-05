package net.blueshell.api.dto.committee

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.dto.AuditedSoftDeleteDTO

@Schema(name = "CommitteeMember")
data class CommitteeMemberDTO(
    @field:NotNull
    var userId: Long? = null,

    @field:NotNull
    var committeeId: Long? = null,

    @field:NotBlank
    var role: String? = null
) : AuditedSoftDeleteDTO()
