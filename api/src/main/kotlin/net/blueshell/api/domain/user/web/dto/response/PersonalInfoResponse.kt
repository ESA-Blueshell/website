package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.domain.user.web.validation.ValidMobilePhoneNumber
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "PersonalInfo")
open class PersonalInfoResponse(
    @field:NotBlank
    @field:Email
    var email: String? = null,

    @field:NotBlank
    var discord: String? = null,

    @field:NotBlank
    @field:ValidMobilePhoneNumber
    var phoneNumber: String? = null
) : AuditedAutoIdDTO()