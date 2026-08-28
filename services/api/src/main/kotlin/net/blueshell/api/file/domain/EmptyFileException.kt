package net.blueshell.api.file.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EmptyFileException :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload empty file")
