package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.validation.group.Administration
import net.blueshell.api.shared.validation.group.Creation
import net.blueshell.api.shared.validation.group.Update
import net.blueshell.api.user.web.validation.UniqueUser

@Schema(name = "SimpleUser")
@UniqueUser(groups = [Update::class, Creation::class, Administration::class])
class SimpleUserDTO(
    @field:NotBlank
    var username: String? = null,

    @field:NotBlank
    var initials: String? = null,

    @field:NotBlank
    var firstName: String? = null,

    var prefix: String? = null,

    @field:NotBlank
    var lastName: String? = null,

    var fullName: String? = null,

    @field:NotNull
    var newsletter: Boolean? = null,

    @field:NotBlank(groups = [Creation::class])
    @field:Size(
        min = 8,
        max = 100,
        message = "Password must be at least 8 characters",
        groups = [Creation::class]
    )
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
        groups = [Creation::class]
    )
    var password: String? = null,

    var addressId: Long? = null,
) : PersonalInfoDTO()
