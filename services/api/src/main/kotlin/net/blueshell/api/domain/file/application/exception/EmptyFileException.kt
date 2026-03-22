package net.blueshell.api.domain.file.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EmptyFileException :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload empty file")
