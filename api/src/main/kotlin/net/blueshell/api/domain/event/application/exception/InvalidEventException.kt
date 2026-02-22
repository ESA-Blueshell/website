package net.blueshell.api.domain.event.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class InvalidEventException(message: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, message)
