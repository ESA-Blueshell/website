package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

@Schema(name = "MemberProfileResponse")
data class MemberProfileResponse(
    var id: Long,
    var userId: Long,
    var dateOfBirth: LocalDate?,
    var studentNumber: String?,
    var gender: Gender?,
    var nationality: String?,
    var bhv: Boolean,
    var ehbo: Boolean,
    var version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)
