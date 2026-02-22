package net.blueshell.api.domain.board.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class BoardMemberNotFoundException(boardId: Long, userId: Long) :
    ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Board member not found: boardId=$boardId, userId=$userId"
    )
