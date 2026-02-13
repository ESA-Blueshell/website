package net.blueshell.api.domain.board.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import java.time.LocalDate

@Schema(name = "BoardResponse")
data class BoardResponse(
    var name: String? = null,
    var candidate: String? = null,
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null,
    var pictureId: Long? = null,
    var members: List<BoardMemberResponse>? = null
) : AuditedAutoIdDTO()
