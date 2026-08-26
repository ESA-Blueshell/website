package net.blueshell.api.domain.board.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "LinkBoardMemberRequest")
data class LinkBoardMemberRequest(
    @Schema(description = "The member to attach to the seat; absent detaches it")
    var userId: Long? = null,
)
