package net.blueshell.api.file.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** The imaging service refused one width; the ladder counts it and goes on. */
class WebpConversionException : ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded image could not be converted")
