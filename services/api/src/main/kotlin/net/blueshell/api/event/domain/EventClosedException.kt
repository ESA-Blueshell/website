package net.blueshell.api.event.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventClosedException(eventId: Long) :
    ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Event $eventId is closed for sign-ups"
    )
