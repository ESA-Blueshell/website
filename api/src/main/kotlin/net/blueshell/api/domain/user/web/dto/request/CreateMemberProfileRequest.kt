package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Schema(name = "CreateMemberProfileRequest")
data class CreateMemberProfileRequest(
    @field:NotNull
    var userId: Long? = null,

    @field:NotNull
    var dateOfBirth: LocalDate? = null,

    @field:NotBlank
    var studentNumber: String? = null,

    @field:NotBlank
    var gender: String? = null,

    @field:NotNull
    var photoConsent: Boolean? = null,

    @field:NotBlank
    var nationality: String? = null,

    @field:NotNull
    var bhv: Boolean? = null,

    @field:NotNull
    var ehbo: Boolean? = null
)
