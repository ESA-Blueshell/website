package net.blueshell.api.domain.event.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventBannerNotFoundException(eventId: Long, fileId: Long) :
    ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "EventBanner not found for event: $eventId and file: $fileId"
    )
