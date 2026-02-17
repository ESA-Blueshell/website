package net.blueshell.api.domain.user.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class UpsertMemberProfileRequest(
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

    var version: Long? = null
)
