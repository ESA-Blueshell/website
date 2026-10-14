package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "ActivationResponse")
data class ActivationResponse(
    val membershipStarted: Boolean,
    @field:Schema(description = "The first contribution is collected by the next incasso run, so no transfer was asked.")
    val collectedByIncasso: Boolean,
)
