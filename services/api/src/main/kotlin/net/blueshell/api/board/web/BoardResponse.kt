package net.blueshell.api.board.web

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

@Schema(name = "BoardResponse")
data class BoardResponse(
    var id: Long,
    @Schema(description = "The board's place in the line, unique among the boards that exist")
    var number: Int,
    @Schema(description = "The name the board chose for itself, where one is recorded")
    var name: String? = null,
    var candidate: String,
    @Schema(description = "The board's shouted line")
    var cheer: String? = null,
    @Schema(description = "The board's own colour; absent means the association's blue")
    var accent: String? = null,
    @Schema(description = "What the year was about, in the board's own words")
    var description: String? = null,
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
