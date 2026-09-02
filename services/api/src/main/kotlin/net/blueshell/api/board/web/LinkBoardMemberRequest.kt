package net.blueshell.api.board.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "LinkBoardMemberRequest")
data class LinkBoardMemberRequest(
    @Schema(description = "The account to attach to the membership; absent detaches it")
    var userId: Long? = null,
)
