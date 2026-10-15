package net.blueshell.api.file.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * The imaging service refused one width of a stored picture. The service's own words are logged
 * where it refused, and the width is counted as refused rather than failing the ladder.
 */
class WebpConversionException : ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded image could not be converted")
