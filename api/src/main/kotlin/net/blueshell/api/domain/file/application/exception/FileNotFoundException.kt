package net.blueshell.api.domain.file.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class FileNotFoundException(identifier: String) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $identifier")
