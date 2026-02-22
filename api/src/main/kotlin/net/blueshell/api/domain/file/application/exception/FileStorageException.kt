package net.blueshell.api.domain.file.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class FileStorageException(message: String, cause: Throwable? = null) :
    ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, cause)
