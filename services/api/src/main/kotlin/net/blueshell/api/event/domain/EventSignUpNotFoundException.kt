package net.blueshell.api.event.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventSignUpNotFoundException(userId: Long, eventId: Long) :
    ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "EventSignUp not found for user: $userId and event: $eventId"
    )
