package net.blueshell.api.domain.user.web.dto.request

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.sql.Date

@Schema(name = "UpdateMemberProfileRequest")
data class UpdateMemberProfileRequest(
    @field:NotNull
    var dateOfBirth: Date? = null,

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
