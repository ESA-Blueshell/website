package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Schema(name = "CreateMemberProfileRequest")
data class CreateMemberProfileRequest(
    var userId: Long,

    var dateOfBirth: LocalDate,

    var studentNumber: String? = null,

    var gender: String? = null,

    @field:NotBlank
    var nationality: String,

    var bhv: Boolean,

    var ehbo: Boolean,

    @Schema(description = "Whether this member's real name may appear in a roster")
    var nameOnRosters: Boolean = false
)
