package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(name = "BoardUpdateUserRequest")
class BoardUpdateUserRequest(
    @field:NotBlank
    var username: String? = null,

    @field:NotBlank
    var initials: String? = null,

    @field:NotBlank
    var firstName: String? = null,

    var prefix: String? = null,

    @field:NotBlank
    var lastName: String? = null,

    @field:NotBlank
    var email: String? = null,
) : UpdateUserRequest()
