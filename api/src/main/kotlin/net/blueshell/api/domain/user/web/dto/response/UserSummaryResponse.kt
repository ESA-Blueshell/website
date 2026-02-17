package net.blueshell.api.domain.user.web.dto.response

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
    var discord: String,
    var phoneNumber: String
)
