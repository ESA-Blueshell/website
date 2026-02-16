package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.sql.Date

@Schema(name = "UpdateUserRequest")
data class UpdateUserRequest(
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

    var addressId: Long? = null,

    @field:NotBlank
    var email: String? = null,

    @field:NotBlank
    var discord: String? = null,

    @field:NotBlank
    var phoneNumber: String? = null,

    var studies: List<UserStudyRequest>? = null,

    var version: Long? = null
)
