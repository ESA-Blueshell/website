package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Schema(name = "UpdateMemberProfileRequest")
data class UpdateMemberProfileRequest(
    var dateOfBirth: LocalDate,

    var studentNumber: String? = null,

    var gender: String? = null,

    @field:NotBlank
    var nationality: String,

    var bhv: Boolean,

    var ehbo: Boolean,

    @Schema(description = "Whether this member's real name may appear on the team pages")
    var nameOnTeamPages: Boolean = false,

    var version: Long
)
