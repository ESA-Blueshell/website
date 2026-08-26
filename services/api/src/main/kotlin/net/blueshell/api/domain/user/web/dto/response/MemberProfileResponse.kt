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
    var gender: String?,
    var nationality: String?,
    var bhv: Boolean,
    var ehbo: Boolean,
    @Schema(description = "Whether this member's real name may appear on the team pages")
    var nameOnTeamPages: Boolean,
    var conditionsAcceptedAt: Instant?,
    var version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)
