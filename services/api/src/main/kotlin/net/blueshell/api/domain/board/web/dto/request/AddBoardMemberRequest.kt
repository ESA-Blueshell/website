package net.blueshell.api.domain.board.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Schema(name = "AddBoardMemberRequest")
data class AddBoardMemberRequest(
    var userId: Long,

    @field:NotBlank(message = "Role is required")
    var role: String,

    var startDate: LocalDate,

    var endDate: LocalDate? = null
)
