package net.blueshell.api.committee.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class CommitteeNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Committee with id $id not found")
