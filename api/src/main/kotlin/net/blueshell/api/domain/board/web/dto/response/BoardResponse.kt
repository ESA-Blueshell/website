package net.blueshell.api.domain.board.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

@Schema(name = "BoardResponse")
data class BoardResponse(
    var id: Long,
    var name: String,
    var candidate: String,
    var startDate: LocalDate,
    var endDate: LocalDate? = null,
    var pictureId: Long? = null,
    var members: List<BoardMemberResponse>,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
