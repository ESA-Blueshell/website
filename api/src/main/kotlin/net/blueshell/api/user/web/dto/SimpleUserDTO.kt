package net.blueshell.api.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.validation.group.Administration
import net.blueshell.api.shared.validation.group.Creation
import net.blueshell.api.shared.validation.group.Update

@Schema(name = "SimpleUser")
@net.blueshell.api.user.web.validation.UniqueUser(groups = [Update::class, Creation::class, Administration::class])
class SimpleUserDTO(
    email: String,

    var fullName: String? = null,

    @field:NotBlank
    var initials: String? = null,

    var prefix: String? = null,

    @field:NotBlank
    var username: String,

    @field:NotNull
    var newsletter: Boolean = false,

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
) : PersonalInfoDTO(email)
