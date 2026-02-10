package net.blueshell.api.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.MappedSuperclass
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.user.web.validation.ValidMobilePhoneNumber

@Schema(name = "PersonalInfo")
@MappedSuperclass
class PersonalInfoDTO(
    @field:NotBlank
    var discord: String,

    @field:NotBlank
    @field:Email
    var email: String,

    @field:NotBlank
    @field:ValidMobilePhoneNumber
    var phoneNumber: String
) : AuditedAutoIdDTO()
