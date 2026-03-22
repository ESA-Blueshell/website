package net.blueshell.api.domain.telemetry.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class InvalidTelemetryException(message: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, message)
