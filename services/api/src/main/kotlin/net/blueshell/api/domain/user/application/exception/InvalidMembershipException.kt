package net.blueshell.api.domain.user.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class InvalidMembershipException(message: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, message)
