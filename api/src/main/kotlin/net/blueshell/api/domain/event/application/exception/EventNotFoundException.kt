package net.blueshell.api.domain.event.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Event with id $id not found")
