package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.MappedSuperclass
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.user.ValidMobilePhoneNumber

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "PersonalInfo")
@MappedSuperclass
open class PersonalInfoDTO : BaseDTO() {
    @NotBlank
    private val discord: @NotBlank String? = null

    @NotBlank
    @Email
    private val email: @NotBlank @Email String? = null

    @NotBlank
    @ValidMobilePhoneNumber
    private val phoneNumber: @NotBlank String? = null
}
