package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "LastRecoveryEmail")
data class LastRecoveryEmailResponse(
    val userId: Long,
    val sentAt: Instant,
)

@Schema(name = "LastRecoveryEmailsResponse", description = "When each account was last sent an activation or a password reset.")
data class LastRecoveryEmailsResponse(
    val emails: List<LastRecoveryEmailResponse>,
)
