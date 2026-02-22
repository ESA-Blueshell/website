package net.blueshell.api.domain.committee.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class CommitteeNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Committee with id $id not found")
