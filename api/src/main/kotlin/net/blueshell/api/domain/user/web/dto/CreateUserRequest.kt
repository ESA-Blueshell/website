package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.validation.group.Creation
import java.sql.Date

@Schema(name = "CreateUserRequest")
data class CreateUserRequest(
    var dateOfBirth: Date? = null,

    var nationality: String? = null,

    var photoConsent: Boolean? = null,

    var ehbo: Boolean? = null,

    var bhv: Boolean? = null,

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
    var phoneNumber: String? = null,
    var studies: List<UserStudyRequest>? = null
)
