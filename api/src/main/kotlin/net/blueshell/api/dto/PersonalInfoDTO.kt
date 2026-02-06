package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.MappedSuperclass
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.dto.base.AuditedAutoIdDTO
import net.blueshell.api.validation.user.ValidMobilePhoneNumber

@Schema(name = "PersonalInfo")
@MappedSuperclass
class PersonalInfoDTO(
    @field:NotBlank
    var discord: String? = null,

    @field:NotBlank
    @field:Email
    var email: String? = null,

    @field:NotBlank
    @field:ValidMobilePhoneNumber
    var phoneNumber: String? = null
) : AuditedAutoIdDTO()
