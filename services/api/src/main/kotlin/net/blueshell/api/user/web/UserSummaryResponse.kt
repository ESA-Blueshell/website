package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "UserSummaryResponse")
data class UserSummaryResponse(
    var id: Long,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
    var fullName: String,
    var email: String,
    var discord: String?,
    @field:Schema(description = "The linked Discord member's user ID, where one is picked")
    var discordId: String? = null,
    var phoneNumber: String?,
)
