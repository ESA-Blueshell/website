package net.blueshell.api.board.web

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
    @Schema(description = "Asset file name of the board's own photograph")
    var image: String? = null,
    var members: List<BoardMemberResponse>,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
