package net.blueshell.api.event.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class GuestNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Guest with id $id not found")
