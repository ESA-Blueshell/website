package net.blueshell.api.board.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.file.api.Image
import java.time.Instant
import java.time.LocalDate

@Schema(name = "BoardMemberResponse")
data class BoardMemberResponse(
    @Schema(description = "The seat's own id, which is how it is edited or removed")
    var id: Long,
    @Schema(description = "The member holding the seat, when one is linked to it")
    var userId: Long? = null,
    var boardId: Long,
    var role: String,
    @Schema(description = "Who held the seat: the linked member's name, or the recorded one")
    var name: String? = null,
    @Schema(description = "The name the seat was known by, beside the name rather than inside it")
    var nickname: String? = null,
    @Schema(description = "The personal note the board page shows beside a member")
    var description: String? = null,
    @Schema(description = "Asset file name of the member's portrait")
    var image: String? = null,
    @Schema(description = "The seat's portrait, with its size and the widths it is stored at")
    var portrait: Image? = null,
    var startDate: LocalDate,
    var endDate: LocalDate? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
