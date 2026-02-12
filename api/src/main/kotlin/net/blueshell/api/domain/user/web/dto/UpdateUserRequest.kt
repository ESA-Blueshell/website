package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.user.web.validation.UniqueUser
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.validation.group.Update
import java.sql.Date

@Schema(name = "UpdateUserRequest")
@UniqueUser(groups = [Update::class])
data class UpdateUserRequest(
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

    var addressId: Long? = null,

    @field:NotBlank
    var email: String? = null,

    @field:NotBlank
    var discord: String? = null,

    @field:NotBlank
    var phoneNumber: String? = null,

    var version: Long? = null
)
