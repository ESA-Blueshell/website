package net.blueshell.api.domain.board.web.mapping.response

import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.board.web.dto.response.BoardMemberResponse
import net.blueshell.api.domain.board.web.dto.response.BoardResponse
import tech.mappie.api.ObjectMappie

object BoardToBoardResponseMapper : ObjectMappie<Board, BoardResponse>() {
    override fun map(from: Board) = mapping {
        BoardResponse::pictureId fromValue from.picture?.id
        BoardResponse::members fromValue from.members.map { it.asResponse() }
    }
}

fun Board.asResponse(): BoardResponse = BoardToBoardResponseMapper.map(this)

object BoardMemberToBoardMemberResponseMapper : ObjectMappie<BoardMember, BoardMemberResponse>()

fun BoardMember.asResponse(): BoardMemberResponse = BoardMemberToBoardMemberResponseMapper.map(this)
