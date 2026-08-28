package net.blueshell.api.board.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class BoardNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Board with id $id not found")
