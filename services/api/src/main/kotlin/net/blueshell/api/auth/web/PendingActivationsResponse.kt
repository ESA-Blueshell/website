package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.TokenPurpose

@Schema(
    name = "PendingActivation",
    description = "The activation email an account that has not been activated takes.",
)
data class PendingActivation(
    @field:Schema(description = "The account.", example = "42")
    val userId: Long,

    @field:Schema(description = "Which activation email applies to it.")
    val purpose: TokenPurpose,
)

@Schema(
    name = "PendingActivationsResponse",
    description = "Which activation email applies to each account that has not been activated. " +
        "Accounts that are already active do not appear.",
)
data class PendingActivationsResponse(
    val activations: List<PendingActivation>,
)
