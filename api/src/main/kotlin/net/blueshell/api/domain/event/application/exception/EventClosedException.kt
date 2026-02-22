package net.blueshell.api.domain.event.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventClosedException(eventId: Long) :
    ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Event $eventId is closed for sign-ups"
    )
