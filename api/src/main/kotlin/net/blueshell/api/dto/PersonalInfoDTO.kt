package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.MappedSuperclass
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.user.ValidMobilePhoneNumber
@Schema(name = "PersonalInfo")
@MappedSuperclass
open class PersonalInfoDTO : BaseDTO() {
    @NotBlank
    val discord: @NotBlank String? = null

    @NotBlank
    @Email
    val email: @NotBlank @Email String? = null

    @NotBlank
    @ValidMobilePhoneNumber
    val phoneNumber: @NotBlank String? = null
}
