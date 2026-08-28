package net.blueshell.api.telemetry.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class TelemetryNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Telemetry with id $id not found")
