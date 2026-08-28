package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "ActivationResponse")
data class ActivationResponse(
    val membershipStarted: Boolean
)
