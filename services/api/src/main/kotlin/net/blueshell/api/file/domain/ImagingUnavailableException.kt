package net.blueshell.api.file.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** The imaging service failed for reasons that are not the picture's, so it is a 503. */
class ImagingUnavailableException(
    message: String,
    cause: Throwable? = null,
) : ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, cause)
