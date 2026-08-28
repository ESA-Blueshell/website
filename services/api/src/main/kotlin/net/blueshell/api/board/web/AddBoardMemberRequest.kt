package net.blueshell.api.board.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(name = "AddBoardMemberRequest")
data class AddBoardMemberRequest(
    @Schema(description = "The member holding the seat; absent for somebody with no account")
    var userId: Long? = null,

    @field:NotBlank(message = "Role is required")
    var role: String,

    var startDate: LocalDate,

    var endDate: LocalDate? = null,

    @Schema(description = "Who held the seat, when no account can be attached to it")
    @field:Size(max = 128, message = "Name must be at most 128 characters")
    var displayName: String? = null,

    var description: String? = null,

    @Schema(description = "Asset file name of the member's portrait")
    @field:Size(max = 255, message = "Image must be at most 255 characters")
    var image: String? = null,
)
