package net.blueshell.api.domain.board.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

@Schema(name = "BoardMemberResponse")
data class BoardMemberResponse(
    var userId: Long,
    var boardId: Long,
    var role: BoardRole,
    var startDate: LocalDate,
    var endDate: LocalDate? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
