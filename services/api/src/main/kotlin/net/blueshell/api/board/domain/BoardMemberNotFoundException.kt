package net.blueshell.api.board.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class BoardMemberNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Board member $id not found")
