package net.blueshell.api.domain.event.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventSignUpNotFoundException(userId: Long, eventId: Long) :
    ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "EventSignUp not found for user: $userId and event: $eventId"
    )
