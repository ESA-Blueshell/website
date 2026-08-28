package net.blueshell.api.committee.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class InvalidCommitteeException(message: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, message)
