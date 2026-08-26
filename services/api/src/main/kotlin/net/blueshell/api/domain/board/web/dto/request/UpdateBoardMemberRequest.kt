package net.blueshell.api.domain.board.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(name = "UpdateBoardMemberRequest")
data class UpdateBoardMemberRequest(
    @field:NotBlank(message = "Role is required")
    var role: String,

    var startDate: LocalDate,

    var endDate: LocalDate? = null,

    @field:Size(max = 128, message = "Name must be at most 128 characters")
    var displayName: String? = null,

    var description: String? = null,

    @field:Size(max = 255, message = "Image must be at most 255 characters")
    var image: String? = null,
)
