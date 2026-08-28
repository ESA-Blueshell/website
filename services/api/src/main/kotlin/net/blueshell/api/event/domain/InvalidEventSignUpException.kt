package net.blueshell.api.event.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class InvalidEventSignUpException(message: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, message)
