package net.blueshell.api.domain.board.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.BaseDTO
import java.time.LocalDate

@Schema(name = "UpdateBoardRequest")
data class UpdateBoardRequest(
    @field:NotBlank(message = "Board name is required")
    @field:Size(min = 1, max = 100, message = "Board name must be between 1 and 100 characters")
    var name: String? = null,

    @field:NotBlank(message = "Candidate name is required")
    var candidate: String? = null,

    @field:NotNull(message = "Start date is required")
    var startDate: LocalDate? = null,

    var endDate: LocalDate? = null,

    var pictureId: Long? = null,

    var version: Long? = null
) : BaseDTO()
