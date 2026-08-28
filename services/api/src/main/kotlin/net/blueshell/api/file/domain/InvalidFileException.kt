package net.blueshell.api.file.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class InvalidFileException(message: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, message)
