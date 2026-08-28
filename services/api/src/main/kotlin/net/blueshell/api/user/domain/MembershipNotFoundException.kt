package net.blueshell.api.user.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class MembershipNotFoundException(id: Long?) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found with id: $id")
