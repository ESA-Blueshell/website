package net.blueshell.api.board.web

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember

fun Board.asResponse(): BoardResponse =
    BoardResponse(
        id = this.id!!,
        number = this.number,
        name = this.name,
        candidate = this.candidate,
        cheer = this.cheer,
        accent = this.accent,
        description = this.description,
        startDate = this.startDate,
        endDate = this.endDate,
        pictureId = this.pictureId,
        image = this.image,
        members = this.members.sortedBy { it.id }.map { it.asResponse() },
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun BoardMember.asResponse(): BoardMemberResponse =
    BoardMemberResponse(
        id = this.id!!,
        userId = this.userId,
        boardId = this.boardId,
        role = this.role,
        name = this.name,
        nickname = this.nickname,
        description = this.description,
        image = this.image,
        startDate = this.startDate,
        endDate = this.endDate,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
