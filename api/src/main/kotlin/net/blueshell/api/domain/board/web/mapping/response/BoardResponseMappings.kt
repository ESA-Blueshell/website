package net.blueshell.api.domain.board.web.mapping.response

import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.board.web.dto.response.BoardMemberResponse
import net.blueshell.api.domain.board.web.dto.response.BoardResponse

fun Board.asResponse(): BoardResponse =
    BoardResponse(
        id = this.id!!,
        name = this.name,
        candidate = this.candidate,
        startDate = this.startDate,
        endDate = this.endDate,
        pictureId = this.pictureId,
        members = this.members.map { it.asResponse() },
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun BoardMember.asResponse(): BoardMemberResponse =
    BoardMemberResponse(
        userId = this.userId,
        boardId = this.boardId,
        role = this.role,
        startDate = this.startDate,
        endDate = this.endDate,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
