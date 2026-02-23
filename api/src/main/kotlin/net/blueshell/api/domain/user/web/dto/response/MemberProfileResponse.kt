package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.sql.Date
import java.time.Instant

@Schema(name = "MemberProfileResponse")
data class MemberProfileResponse(
    var id: Long,
    var userId: Long,
    var dateOfBirth: Date,
    var studentNumber: String,
    var gender: String,
    var photoConsent: Boolean,
    var nationality: String,
    var bhv: Boolean,
    var ehbo: Boolean,
    var version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)
