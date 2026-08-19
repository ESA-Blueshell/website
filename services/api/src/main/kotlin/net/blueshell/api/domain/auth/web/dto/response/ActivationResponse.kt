package net.blueshell.api.domain.auth.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "ActivationResponse")
data class ActivationResponse(
    val membershipStarted: Boolean
)
