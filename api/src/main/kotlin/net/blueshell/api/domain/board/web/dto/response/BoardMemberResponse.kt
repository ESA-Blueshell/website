package net.blueshell.api.domain.board.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedDTO
import java.time.LocalDate

@Schema(name = "BoardMemberResponse")
data class BoardMemberResponse(
    var userId: Long? = null,
    var boardId: Long? = null,
    var role: String? = null,
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null
) : AuditedDTO()
