package net.blueshell.api.file.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class FileStorageException(message: String, cause: Throwable? = null) :
    ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, cause)
