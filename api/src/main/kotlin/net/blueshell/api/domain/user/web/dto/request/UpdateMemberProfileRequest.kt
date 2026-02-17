package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@Schema(name = "UpdateMemberProfileRequest")
data class UpdateMemberProfileRequest(
    @field:NotNull
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
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
    var ehbo: Boolean? = null,

    @field:NotNull
    var version: Long? = null
)
