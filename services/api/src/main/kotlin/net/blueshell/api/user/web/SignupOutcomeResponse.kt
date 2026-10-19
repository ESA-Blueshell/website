package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "SignupOutcomeResponse")
data class SignupOutcomeResponse(
    val emailConfirmed: Boolean,
    val membershipStarted: Boolean,
    @field:Schema(description = "The first contribution is collected by the next incasso run, so no transfer was asked.")
    val collectedByIncasso: Boolean,
)
