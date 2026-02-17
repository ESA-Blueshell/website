package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "UserSummaryResponse")
data class UserSummaryResponse(
    var fullName: String,
    var email: String,
    var discord: String,
    var phoneNumber: String
)