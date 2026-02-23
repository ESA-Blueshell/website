package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(name = "UpdateUserRequest")
open class UpdateUserRequest(
    @field:NotNull
    var newsletter: Boolean? = null,

    @field:NotBlank
    var discord: String? = null,

    @field:NotBlank
    var phoneNumber: String? = null,

    @field:Valid
    var memberProfile: UpsertMemberProfileRequest? = null,

    @field:NotNull
    var version: Long? = null
) : UpdateUserPayload
