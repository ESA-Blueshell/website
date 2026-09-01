package net.blueshell.api.board.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(name = "UpdateBoardRequest")
data class UpdateBoardRequest(
    @Schema(description = "The board's place in the line; the ninth board is 9")
    @field:Min(value = 1, message = "Board number must be at least 1")
    var number: Int,

    @Schema(description = "The name the board chose for itself; blank for a board with none")
    @field:Size(max = 100, message = "Board name must be at most 100 characters")
    var name: String? = null,

    @Schema(description = "Kept for the column behind it; the board's own name is used when blank")
    var candidate: String? = null,

    @Schema(description = "The board's shouted line")
    @field:Size(max = 255, message = "Cheer must be at most 255 characters")
    var cheer: String? = null,

    @Schema(description = "The board's own colour; blank means the association's blue")
    @field:Size(max = 32, message = "Accent must be at most 32 characters")
    var accent: String? = null,

    @Schema(description = "What the year was about, in the board's own words")
    var description: String? = null,

    var startDate: LocalDate,

    var endDate: LocalDate? = null,

    var pictureId: Long? = null,

    @Schema(description = "Asset file name of the board's photograph")
    @field:Size(max = 255, message = "Image must be at most 255 characters")
    var image: String? = null,

    var version: Long
)
