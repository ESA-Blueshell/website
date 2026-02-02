package net.blueshell.api.dto.committee

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "CommitteeMember")
class CommitteeMemberDTO : BaseDTO() {
    private val id: Long? = null

    @NotBlank
    private val userId: @NotBlank Long? = null
    private val committeeId: Long? = null

    @NotBlank
    private val role: @NotBlank String? = null
}
