package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.MappedSuperclass
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.user.ValidMobilePhoneNumber

@Schema(name = "PersonalInfo")
@MappedSuperclass
open class PersonalInfoDTO(
    @field:NotBlank
    open var discord: String? = null,

    @field:NotBlank
    @field:Email
    open var email: String? = null,

    @field:NotBlank
    @field:ValidMobilePhoneNumber
    open var phoneNumber: String? = null
) : BaseDTO()
