package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.user.web.validation.UniqueUser
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.validation.group.Administration
import net.blueshell.api.shared.validation.group.Creation
import java.sql.Date

@Schema(name = "CreateUserRequest")
@UniqueUser(groups = [Creation::class, Administration::class])
data class CreateUserRequest(
    @field:NotNull
    var roles: Set<Role>? = null,

    @field:NotNull
    var dateOfBirth: Date? = null,

    @field:NotBlank
    var nationality: String? = null,

    @field:NotNull
    var photoConsent: Boolean? = null,

    @field:NotNull
    var ehbo: Boolean? = null,

    @field:NotNull
    var bhv: Boolean? = null,

    @field:NotNull
    var enabled: Boolean? = null,

    var gender: String? = null,
    var studentNumber: String? = null,

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

    @field:NotBlank
    var email: String? = null,

    @field:NotBlank
    var discord: String? = null,

    @field:NotBlank
    var phoneNumber: String? = null
)
