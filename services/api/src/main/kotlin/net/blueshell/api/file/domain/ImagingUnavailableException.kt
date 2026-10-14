package net.blueshell.api.file.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * The imaging service could not convert a picture for reasons that are not the picture's: it is
 * down, busy past its wait, or answered something it should not have. Nobody's upload is wrong,
 * so it is a 503 and worth trying again.
 */
class ImagingUnavailableException(
    message: String,
    cause: Throwable? = null,
) : ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, cause)
