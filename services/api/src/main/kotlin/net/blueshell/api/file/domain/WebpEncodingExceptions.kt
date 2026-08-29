package net.blueshell.api.file.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * The converter could not be run at all.
 *
 * A packaging fault rather than anybody's picture: the startup probe exists so that this is
 * found at deploy, and an upload that still meets it has hit something the site owns.
 */
class WebpUnavailableException(message: String, cause: Throwable? = null) :
    ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, cause)

/**
 * The converter ran and refused what it was handed.
 *
 * A picture whose header reads but whose content does not decode arrives here, and the person
 * who chose it is the only one who can do anything about it. The converter's own words name a
 * temporary path, so they are logged rather than answered.
 */
class WebpConversionException :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded image could not be converted")
