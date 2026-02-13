package net.blueshell.api.domain.board.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Schema(name = "AddBoardMemberRequest")
data class AddBoardMemberRequest(
    @field:NotNull(message = "User ID is required")
    var userId: Long? = null,

    @field:NotBlank(message = "Role is required")
    var role: String? = null,

    @field:NotNull(message = "Start date is required")
    var startDate: LocalDate? = null,

    var endDate: LocalDate? = null
)
