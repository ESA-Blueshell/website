package net.blueshell.api.domain.user.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class MembershipNotFoundException(id: Long?) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found with id: $id")
